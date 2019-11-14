// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil.Feature;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MostlySingularMultiMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class HighlightVisitorImpl extends JavaElementVisitor implements HighlightVisitor {
  private HighlightInfoHolder myHolder;
  private LanguageLevel myLanguageLevel;
  private JavaSdkVersion myJavaSdkVersion;

  private PsiFile myFile;
  private PsiJavaModule myJavaModule;

  // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new THashMap<>();
  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new THashMap<>();

  // value==1: no info if the parameter was reassigned (but the parameter is present in current file), value==2: parameter was reassigned
  private final TObjectIntHashMap<PsiParameter> myReassignedParameters = new TObjectIntHashMap<>();

  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> mySingleImportedClasses = new THashMap<>();
  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiField>> mySingleImportedFields = new THashMap<>();

  private final PsiElementVisitor REGISTER_REFERENCES_VISITOR = new PsiRecursiveElementWalkingVisitor() {
    @Override public void visitElement(PsiElement element) {
      super.visitElement(element);
    }
  };
  private final Map<PsiClass, MostlySingularMultiMap<MethodSignature, PsiMethod>> myDuplicateMethods = new THashMap<>();
  private final Set<PsiClass> myOverrideEquivalentMethodsVisitedClasses = new THashSet<>();
  private final Map<PsiMethod, PsiType> myExpectedReturnTypes = new HashMap<>();

  private static class Holder {
    private static final boolean CHECK_ELEMENT_LEVEL = ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal();
  }

  @NotNull
  protected PsiResolveHelper getResolveHelper(@NotNull Project project) {
    return PsiResolveHelper.SERVICE.getInstance(project);
  }

  protected HighlightVisitorImpl() {
  }

  /**
   * @deprecated use {@link #HighlightVisitorImpl()} and {@link #getResolveHelper(Project)}
   */
  @Deprecated
  protected HighlightVisitorImpl(@SuppressWarnings("unused") @NotNull PsiResolveHelper psiResolveHelper) {
  }

  @NotNull
  private MostlySingularMultiMap<MethodSignature, PsiMethod> getDuplicateMethods(@NotNull PsiClass aClass) {
    MostlySingularMultiMap<MethodSignature, PsiMethod> signatures = myDuplicateMethods.get(aClass);
    if (signatures == null) {
      signatures = new MostlySingularMultiMap<>();
      for (PsiMethod method : aClass.getMethods()) {
        if (method instanceof ExternallyDefinedPsiElement) continue; // ignore aspectj-weaved methods; they are checked elsewhere
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        signatures.add(signature, method);
      }

      myDuplicateMethods.put(aClass, signatures);
    }
    return signatures;
  }

  @NotNull
  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public HighlightVisitorImpl clone() {
    return new HighlightVisitorImpl();
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    // both PsiJavaFile and PsiCodeFragment must match
    return file instanceof PsiImportHolder && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (Holder.CHECK_ELEMENT_LEVEL) {
      ((CheckLevelHighlightInfoHolder)myHolder).enterLevel(element);
      element.accept(this);
      ((CheckLevelHighlightInfoHolder)myHolder).enterLevel(null);
    }
    else {
      element.accept(this);
    }
  }

  private void registerReferencesFromInjectedFragments(@NotNull PsiElement element) {
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFile.getProject());
    manager.enumerateEx(element, myFile, false, (injectedPsi, places) -> injectedPsi.accept(REGISTER_REFERENCES_VISITOR));
  }

  @Override
  public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable highlight) {
    boolean success = true;
    try {
      prepare(Holder.CHECK_ELEMENT_LEVEL ? new CheckLevelHighlightInfoHolder(file, holder) : holder, file);
      if (updateWholeFile) {
        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress == null) throw new IllegalStateException("Must be run under progress");
        Project project = file.getProject();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        TextRange dirtyScope = document == null ? null : DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap().getFileDirtyScope(document, Pass.UPDATE_ALL);
        if (dirtyScope == null) dirtyScope = file.getTextRange();

        success = true;
      }
      else {
        highlight.run();
      }
    }
    finally {
      myUninitializedVarProblems.clear();
      myFinalVarProblems.clear();
      mySingleImportedClasses.clear();
      mySingleImportedFields.clear();
      myReassignedParameters.clear();

      myJavaModule = null;
      myFile = null;
      myHolder = null;
      myDuplicateMethods.clear();
      myOverrideEquivalentMethodsVisitedClasses.clear();
      myExpectedReturnTypes.clear();
    }

    return success;
  }

  private void prepare(@NotNull HighlightInfoHolder holder, @NotNull PsiFile file) {
    myHolder = holder;
    myFile = file;
    myLanguageLevel = PsiUtil.getLanguageLevel(file);
    myJavaSdkVersion = ObjectUtils
      .notNull(JavaVersionService.getInstance().getJavaSdkVersion(file), JavaSdkVersion.fromLanguageLevel(myLanguageLevel));
    myJavaModule = myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9) ? JavaModuleGraphUtil.findDescriptorByElement(file) : null;
  }

  @Override
  public void visitElement(PsiElement element) {
    if (!(myFile instanceof ServerPageFile)) {
    }
  }

  @Nullable
  public static JavaResolveResult resolveJavaReference(@NotNull PsiReference reference) {
    return reference instanceof PsiJavaReference ? ((PsiJavaReference)reference).advancedResolve(false) : null;
  }

  @Override
  public void visitAnnotation(PsiAnnotation annotation) {

  }

  @Override
  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {

  }

  @Override
  public void visitAnnotationMethod(PsiAnnotationMethod method) {

  }

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {

  }

  @Override
  public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
    
    
     visitExpression(assignment);
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    super.visitPolyadicExpression(expression);
    
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {

  }

  @Override
  public void visitBreakStatement(PsiBreakStatement statement) {
    super.visitBreakStatement(statement);
    
  }

  @Override
  public void visitYieldStatement(PsiYieldStatement statement) {
    super.visitYieldStatement(statement);
    
    
  }

  @Override
  public void visitClass(PsiClass aClass) {
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
  }

  @Override
  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
  }

  @Override
  public void visitComment(PsiComment comment) {
    super.visitComment(comment);
    
  }

  @Override
  public void visitContinueStatement(PsiContinueStatement statement) {
    super.visitContinueStatement(statement);
    
  }

  @Override
  public void visitJavaToken(PsiJavaToken token) {
    super.visitJavaToken(token);

    IElementType type = token.getTokenType();
    if (!myHolder.hasErrorResults() && type == JavaTokenType.TEXT_BLOCK_LITERAL) {
    }

    if (!myHolder.hasErrorResults() && type == JavaTokenType.RBRACE && token.getParent() instanceof PsiCodeBlock) {
      PsiElement gParent = token.getParent().getParent();
      PsiCodeBlock codeBlock;
      PsiType returnType;
      if (gParent instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)gParent;
        codeBlock = method.getBody();
        returnType = method.getReturnType();
      }
      else if (gParent instanceof PsiLambdaExpression) {
        PsiElement body = ((PsiLambdaExpression)gParent).getBody();
        if (!(body instanceof PsiCodeBlock)) return;
        codeBlock = (PsiCodeBlock)body;
        returnType = LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)gParent);
      }
      else {
        return;
      }
    }
  }

  @Override
  public void visitDocComment(PsiDocComment comment) {
    
  }

  @Override
  public void visitDocTagValue(PsiDocTagValue value) {
  }

  @Override
  public void visitEnumConstant(PsiEnumConstant enumConstant) {

  }

  @Override
  public void visitEnumConstantInitializer(PsiEnumConstantInitializer enumConstantInitializer) {

  }

  @Override
  public void visitExpression(PsiExpression expression) {
    ProgressManager.checkCanceled(); // visitLiteralExpression is invoked very often in array initializers
    super.visitExpression(expression);

    PsiElement parent = expression.getParent();
    PsiType type = expression.getType();

    
    if (!myHolder.hasErrorResults() && expression instanceof PsiArrayAccessExpression) {
    }
    if (!myHolder.hasErrorResults() && parent instanceof PsiNewExpression &&
        ((PsiNewExpression)parent).getQualifier() != expression && ((PsiNewExpression)parent).getArrayInitializer() != expression) {
    }
    
    
    
    
    
    
    if (!myHolder.hasErrorResults() && parent instanceof PsiThrowStatement && ((PsiThrowStatement)parent).getException() == expression && type != null) {
    }
  }

  @Override
  public void visitExpressionList(PsiExpressionList list) {
  }

  @Override
  public void visitField(PsiField field) {
    super.visitField(field);
  }

  @Override
  public void visitForStatement(PsiForStatement statement) {
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement statement) {
  }

  @Override
  public void visitImportStaticStatement(PsiImportStaticStatement statement) {
  }

  @Override
  public void visitIdentifier(final PsiIdentifier identifier) {
    TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;

      if (variable.getInitializer() == null) {
        final PsiElement child = variable.getLastChild();
        if (child instanceof PsiErrorElement && child.getPrevSibling() == identifier) return;
      }

      boolean isMethodParameter = variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiMethod;
      if (isMethodParameter) {
        myReassignedParameters.put((PsiParameter)variable, 1); // mark param as present in current file
      }
      else {
        // method params are highlighted in visitMethod since we should make sure the method body was visited before
      }
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      if (aClass.isAnnotationType()) {
      }
      if (!(parent instanceof PsiAnonymousClass) && aClass.getNameIdentifier() == identifier) {
      }
      if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_10)) {
      }
      if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      }

       {
      }
    }
    else if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      final PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
      }
    }

    super.visitIdentifier(identifier);
  }

  @Override
  public void visitImportStatement(final PsiImportStatement statement) {
     {
    }
  }

  @Override
  public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement ref) {
    final String refName = ref.getReferenceName();
    final JavaResolveResult[] results = ref.multiResolve(false);

    final PsiElement referenceNameElement = ref.getReferenceNameElement();
    if (results.length == 0) {
      final String description = JavaErrorMessages.message("cannot.resolve.symbol", refName);
      assert referenceNameElement != null : ref;
      final HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(referenceNameElement).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createSetupJDKFix());
    }
    else {
      final PsiManager manager = ref.getManager();
      for (JavaResolveResult result : results) {
        final PsiElement element = result.getElement();

        String description = null;
        if (element instanceof PsiClass) {
          final Pair<PsiImportStaticReferenceElement, PsiClass> imported = mySingleImportedClasses.get(refName);
          final PsiClass aClass = Pair.getSecond(imported);
          if (aClass != null && !manager.areElementsEquivalent(aClass, element)) {
            description = imported.first == null
                          ? JavaErrorMessages.message("single.import.class.conflict", refName)
                          : imported.first.equals(ref)
                            ? JavaErrorMessages.message("class.is.ambiguous.in.single.static.import", refName)
                            : JavaErrorMessages.message("class.is.already.defined.in.single.static.import", refName);
          }
          mySingleImportedClasses.put(refName, Pair.create(ref, (PsiClass)element));
        }
        else if (element instanceof PsiField) {
          final Pair<PsiImportStaticReferenceElement, PsiField> imported = mySingleImportedFields.get(refName);
          final PsiField field = Pair.getSecond(imported);
          if (field != null && !manager.areElementsEquivalent(field, element)) {
            description = imported.first.equals(ref)
                          ? JavaErrorMessages.message("field.is.ambiguous.in.single.static.import", refName)
                          : JavaErrorMessages.message("field.is.already.defined.in.single.static.import", refName);
          }
          mySingleImportedFields.put(refName, Pair.create(ref, (PsiField)element));
        }

        if (description != null) {
        }
      }
    }
     {
      PsiElement resolved = results.length >= 1 ? results[0].getElement() : null;
      if (results.length > 1) {
        for (int i = 1; i < results.length; i++) {
          final PsiElement element = results[i].getElement();
          if (resolved instanceof PsiMethod && !(element instanceof PsiMethod) ||
              resolved instanceof PsiVariable && !(element instanceof PsiVariable) ||
              resolved instanceof PsiClass && !(element instanceof PsiClass)) {
            resolved = null;
            break;
          }
        }
      }
      final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (resolved instanceof PsiClass) {
      }
      else {
        if (referenceNameElement != null) {
          if (resolved instanceof PsiVariable) {
          }
          else if (resolved instanceof PsiMethod) {
          }
        }
      }
    }
  }

  @Override
  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    super.visitInstanceOfExpression(expression);
    
    
  }

  @Override
  public void visitKeyword(PsiKeyword keyword) {
    super.visitKeyword(keyword);
    PsiElement parent = keyword.getParent();
    String text = keyword.getText();
    if (parent instanceof PsiModifierList) {
      PsiModifierList psiModifierList = (PsiModifierList)parent;
      
      
      PsiElement pParent = psiModifierList.getParent();
      if (PsiModifier.ABSTRACT.equals(text) && pParent instanceof PsiMethod) {
         {
        }
      }
    }
    else if (PsiKeyword.INTERFACE.equals(text) && parent instanceof PsiClass) {
      
    }
    
    
  }

  @Override
  public void visitLabeledStatement(PsiLabeledStatement statement) {
    super.visitLabeledStatement(statement);
    
    
  }

  @Override
  public void visitLiteralExpression(PsiLiteralExpression expression) {
    super.visitLiteralExpression(expression);
  }

  @Override
  public void visitMethod(PsiMethod method) {
    super.visitMethod(method);
    
    
    
    

    PsiClass aClass = method.getContainingClass();
    if (!myHolder.hasErrorResults() && method.isConstructor()) {
    }
    if (!myHolder.hasErrorResults() && method.hasModifierProperty(PsiModifier.DEFAULT)) {
    }
    if (!myHolder.hasErrorResults() && aClass != null && aClass.isInterface() && method.hasModifierProperty(PsiModifier.STATIC)) {
    }
    if (!myHolder.hasErrorResults() && aClass != null) {
    }

    // method params are highlighted in visitMethod since we should make sure the method body was visited before
    PsiParameter[] parameters = method.getParameterList().getParameters();
    final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

    for (PsiParameter parameter : parameters) {
      int info = myReassignedParameters.get(parameter);
      if (info == 0) continue; // out of this file

      PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
      if (nameIdentifier != null) {
        if (info == 2) { // reassigned
        }
        else {
        }
      }
    }
  }

  private void highlightReferencedMethodOrClassName(@NotNull PsiJavaCodeReferenceElement element, @Nullable PsiElement resolved) {
    PsiElement parent = element.getParent();
    final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)parent).resolveMethod();
      PsiElement methodNameElement = element.getReferenceNameElement();
      if (method != null && methodNameElement != null&& !(methodNameElement instanceof PsiKeyword)) {
      }
    }
    else if (parent instanceof PsiConstructorCall) {
      try {
        PsiMethod method = ((PsiConstructorCall)parent).resolveConstructor();
        PsiMember methodOrClass = method != null ? method : resolved instanceof PsiClass ? (PsiClass)resolved : null;
        if (methodOrClass != null) {
          final PsiElement referenceNameElement = element.getReferenceNameElement();
          if(referenceNameElement != null) {
            // exclude type parameters from the highlighted text range
            TextRange range = referenceNameElement.getTextRange();
          }
        }
      }
      catch (IndexNotReadyException ignored) { }
    }
    else if (resolved instanceof PsiPackage) {
      // highlight package (and following dot) as a class
    }
    else if (resolved instanceof PsiClass) {
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
     visitExpression(expression);
  }

  @Override
  public void visitModifierList(PsiModifierList list) {
    super.visitModifierList(list);
    PsiElement parent = list.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      
      MethodSignatureBackedByPsiMethod methodSignature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
      PsiClass aClass = method.getContainingClass();
      if (!myHolder.hasErrorResults() && aClass != null && myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
      }
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;
      try {

      }
      catch (IndexNotReadyException ignored) {
      }
    }
    else if (parent instanceof PsiEnumConstant) {
      
    }
  }

  @Override
  public void visitNameValuePair(PsiNameValuePair pair) {
  }

  @Override
  public void visitNewExpression(PsiNewExpression expression) {
  }

  @Override
  public void visitPackageStatement(PsiPackageStatement statement) {
    super.visitPackageStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      
    }
  }

  @Override
  public void visitParameter(PsiParameter parameter) {
    super.visitParameter(parameter);

    final PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList && parameter.isVarArgs()) {
      
      
    }
    else if (parent instanceof PsiCatchSection) {
      if (!myHolder.hasErrorResults() && parameter.getType() instanceof PsiDisjunctionType) {
      }
      
      
      
    }
    else if (parent instanceof PsiForeachStatement) {
      
    }
  }

  @Override
  public void visitParameterList(PsiParameterList list) {
    super.visitParameterList(list);
    
  }

  @Override
  public void visitUnaryExpression(PsiUnaryExpression expression) {
    super.visitUnaryExpression(expression);
     {
    }
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = doVisitReferenceElement(ref);
    if (result != null) {
      PsiElement resolved = result.getElement();
      
    }
  }

  private JavaResolveResult doVisitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = resolveOptimised(ref);
    if (result == null) return null;

    PsiElement resolved = result.getElement();
    PsiElement parent = ref.getParent();

    if (parent instanceof PsiJavaCodeReferenceElement || ref.isQualified()) {
      if (!myHolder.hasErrorResults() && resolved instanceof PsiTypeParameter) {
        boolean canSelectFromTypeParameter = myJavaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7);
        if (canSelectFromTypeParameter) {
          final PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
          if (containingClass != null) {
            if (PsiTreeUtil.isAncestor(containingClass.getExtendsList(), ref, false) ||
                PsiTreeUtil.isAncestor(containingClass.getImplementsList(), ref, false)) {
              canSelectFromTypeParameter = false;
            }
          }
        }
        if (!canSelectFromTypeParameter) {
        }
      }
    }

    
    
    
    
     {
    }
    

    if (resolved != null && parent instanceof PsiReferenceList) {
       {
        PsiReferenceList referenceList = (PsiReferenceList)parent;
      }
    }

    if (resolved instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)resolved;

      if (!(variable instanceof PsiField)) {
        PsiElement containingClass = PsiTreeUtil.getNonStrictParentOfType(ref, PsiClass.class, PsiLambdaExpression.class);
        while ((containingClass instanceof PsiAnonymousClass || containingClass instanceof PsiLambdaExpression) &&
               !PsiTreeUtil.isAncestor(containingClass, variable, false)) {
          if (containingClass instanceof PsiLambdaExpression ||
              !PsiTreeUtil.isAncestor(((PsiAnonymousClass)containingClass).getArgumentList(), ref, false)) {
            break;
          }
          containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, PsiLambdaExpression.class);
        }
      }

      if (variable instanceof PsiParameter && ref instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)ref)) {
        myReassignedParameters.put((PsiParameter)variable, 2);
      }

      final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (!variable.hasModifierProperty(PsiModifier.FINAL) && false) {
      }
      else {
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement != null) {
        }
      }
    }
    else {
      highlightReferencedMethodOrClassName(ref, resolved);
    }

    if (parent instanceof PsiNewExpression &&
        !(resolved instanceof PsiClass) &&
        resolved instanceof PsiNamedElement &&
        ((PsiNewExpression)parent).getClassOrAnonymousClassReference() == ref) {
      String text = JavaErrorMessages.message("cannot.resolve.symbol", ((PsiNamedElement)resolved).getName());
    }

    if (!myHolder.hasErrorResults() && resolved instanceof PsiClass) {
      final PsiClass aClass = ((PsiClass)resolved).getContainingClass();
      if (aClass != null) {
        final PsiElement qualifier = ref.getQualifier();
        final PsiElement place;
        if (qualifier instanceof PsiJavaCodeReferenceElement) {
          place = ((PsiJavaCodeReferenceElement)qualifier).resolve();
        }
        else {
          if (parent instanceof PsiNewExpression) {
            final PsiExpression newQualifier = ((PsiNewExpression)parent).getQualifier();
            place = newQualifier == null ? ref : PsiUtil.resolveClassInType(newQualifier.getType());
          }
          else {
            place = ref;
          }
        }
        if (place != null && PsiTreeUtil.isAncestor(aClass, place, false) && aClass.hasTypeParameters()) {
        }
      }
      else if (resolved instanceof PsiTypeParameter) {
        final PsiTypeParameterListOwner owner = ((PsiTypeParameter)resolved).getOwner();
        if (owner instanceof PsiClass) {
          final PsiClass outerClass = (PsiClass)owner;
          if (!InheritanceUtil.hasEnclosingInstanceInScope(outerClass, ref, false, false)) {
          }
        }
      }
    }

    return result;
  }

  @Nullable
  private JavaResolveResult resolveOptimised(@NotNull PsiJavaCodeReferenceElement ref) {
    try {
      if (ref instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(ref, resolver, true, true, myFile);
        return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
      }
      else {
        return ref.advancedResolve(true);
      }
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Nullable
  private JavaResolveResult[] resolveOptimised(@NotNull PsiReferenceExpression expression) {
    try {
      if (expression instanceof PsiReferenceExpressionImpl) {
        PsiReferenceExpressionImpl.OurGenericsResolver resolver = PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE;
        return JavaResolveUtil.resolveWithContainingFile(expression, resolver, true, true, myFile);
      }
      else {
        return expression.multiResolve(true);
      }
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    JavaResolveResult resultForIncompleteCode = doVisitReferenceElement(expression);

     {
      visitExpression(expression);
      if (myHolder.hasErrorResults()) return;
    }

    JavaResolveResult[] results = resolveOptimised(expression);
    if (results == null) return;
    JavaResolveResult result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;

    PsiElement resolved = result.getElement();
    if (resolved instanceof PsiVariable && resolved.getContainingFile() == expression.getContainingFile()) {
      PsiVariable variable = (PsiVariable)resolved;
      boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
      if (isFinal && !variable.hasInitializer()) {
         {
        }
      }
       {
        try {
        }
        catch (IndexNotReadyException ignored) { }
      }
    }

    PsiElement parent = expression.getParent();

    if (!myHolder.hasErrorResults() && resultForIncompleteCode != null) {
    }

    if (!myHolder.hasErrorResults() && resolved instanceof PsiField) {
      try {
      }
      catch (IndexNotReadyException ignored) { }
    }
    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (!myHolder.hasErrorResults() && qualifierExpression != null && myJavaModule == null) {
      PsiType type = qualifierExpression.getType();
      if (type instanceof PsiCapturedWildcardType) {
        type = ((PsiCapturedWildcardType)type).getUpperBound();
      }
      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass == null && qualifierExpression instanceof PsiReferenceExpression) {
        PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolve instanceof PsiClass) {
          psiClass = (PsiClass)resolve;
        }
      }
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (toReportFunctionalExpressionProblemOnParent(parent)) return;
    PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    if (functionalInterfaceType != null && !PsiTypesUtil.allTypeParametersResolved(expression, functionalInterfaceType)) return;

    final JavaResolveResult result;
    final JavaResolveResult[] results;
    try {
      results = expression.multiResolve(true);
      result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    }
    catch (IndexNotReadyException e) {
      return;
    }
    final PsiElement method = result.getElement();
    if (method instanceof PsiJvmMember && !result.isAccessible()) {
      String accessProblem = HighlightUtil.accessProblemDescription(expression, method, result);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(accessProblem).create();
    }
    else {
      final TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (method instanceof PsiMethod && !expression.isConstructor()) {
        PsiElement methodNameElement = expression.getReferenceNameElement();
        if (methodNameElement != null) {
        }
      }
    }

    if (!LambdaUtil.isValidLambdaContext(parent)) {
      String description = "Method reference expression is not expected here";
    }

     {
      PsiElement referenceNameElement = expression.getReferenceNameElement();
      if (referenceNameElement instanceof PsiKeyword) {
        if (!PsiMethodReferenceUtil.isValidQualifier(expression)) {
          PsiElement qualifier = expression.getQualifier();
          if (qualifier != null) {
            String description = "Cannot find class " + qualifier.getText();
          }
        }
      }
    }

    if (functionalInterfaceType != null) {
       {
        boolean isFunctional = LambdaUtil.isFunctionalType(functionalInterfaceType);
        if (!isFunctional) {
          String description = functionalInterfaceType.getPresentableText() + " is not a functional interface";
        }
      }
       {
        checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType);
      }
       {
        String errorMessage = PsiMethodReferenceUtil.checkMethodReferenceContext(expression);
        if (errorMessage != null) {
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(errorMessage).create();
          if (method instanceof PsiMethod &&
              !((PsiMethod)method).isConstructor() &&
              !((PsiMethod)method).hasModifierProperty(PsiModifier.ABSTRACT)) {
            boolean shouldHave = !((PsiMethod)method).hasModifierProperty(PsiModifier.STATIC);
            QuickFixAction.registerQuickFixActions(info, null, JvmElementActionFactories.createModifierActions(
                      (JvmModifiersOwner)method, MemberRequestsKt.modifierRequest(JvmModifier.STATIC, shouldHave)));
          }
        }
      }
    }

    if (method instanceof PsiMethod && ((PsiMethod)method).hasModifierProperty(PsiModifier.STATIC)) {
      if (!myHolder.hasErrorResults() && ((PsiMethod)method).hasTypeParameters()) {
      }

      PsiClass containingClass = ((PsiMethod)method).getContainingClass();
      if (!myHolder.hasErrorResults() && containingClass != null && containingClass.isInterface()) {
      }
    }

     {
    }

     {
    }

     {
      if (results.length == 0 || results[0] instanceof MethodCandidateInfo &&
                                 !((MethodCandidateInfo)results[0]).isApplicable() &&
                                 functionalInterfaceType != null || results.length > 1) {
        String description = null;
        if (results.length == 1) {
          description = ((MethodCandidateInfo)results[0]).getInferenceErrorMessage();
        }

        if (description == null){
          description = JavaErrorMessages.message("cannot.resolve.method", expression.getReferenceName());
        }

        if (description != null) {
          PsiElement referenceNameElement = ObjectUtils.notNull(expression.getReferenceNameElement(), expression);
          HighlightInfoType type = results.length == 0 ? HighlightInfoType.WRONG_REF : HighlightInfoType.ERROR;
          HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(type).descriptionAndTooltip(description).range(referenceNameElement).create();
        }
      }
    }

     {
      final String badReturnTypeMessage = PsiMethodReferenceUtil.checkReturnType(expression, result, functionalInterfaceType);
      if (badReturnTypeMessage != null) {
        HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(badReturnTypeMessage).create();
      }
    }
  }

  /**
   * @return true for {@code functional_expression;} or {@code var l = functional_expression;}
   */
  private static boolean toReportFunctionalExpressionProblemOnParent(@Nullable PsiElement parent) {
    if (parent instanceof PsiLocalVariable) {
      return ((PsiLocalVariable)parent).getTypeElement().isInferredType();
    }
    return parent instanceof PsiExpressionStatement && !(parent.getParent() instanceof PsiSwitchLabeledRuleStatement);
  }

  // 15.13 | 15.27
  // It is a compile-time error if any class or interface mentioned by either U or the function type of U
  // is not accessible from the class or interface in which the method reference expression appears.
  private void checkFunctionalInterfaceTypeAccessible(@NotNull PsiFunctionalExpression expression, @NotNull PsiType functionalInterfaceType) {
    checkFunctionalInterfaceTypeAccessible(expression, functionalInterfaceType, true);
  }
  private boolean checkFunctionalInterfaceTypeAccessible(@NotNull PsiFunctionalExpression expression,
                                                         @NotNull PsiType functionalInterfaceType,
                                                         boolean checkFunctionalTypeSignature) {
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null) {
      return false;
    }
    if (PsiUtil.isAccessible(myFile.getProject(), psiClass, expression, null)) {
      for (PsiType type : resolveResult.getSubstitutor().getSubstitutionMap().values()) {
        if (type != null && checkFunctionalInterfaceTypeAccessible(expression, type, false)) return true;
      }

      PsiMethod psiMethod = checkFunctionalTypeSignature ? LambdaUtil.getFunctionalInterfaceMethod(resolveResult) : null;
      if (psiMethod != null) {
        PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(psiMethod, resolveResult);
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
          PsiType substitute = substitutor.substitute(parameter.getType());
          if (substitute != null && checkFunctionalInterfaceTypeAccessible(expression, substitute, false)) return true;
        }

        PsiType substitute = substitutor.substitute(psiMethod.getReturnType());
        return substitute != null && checkFunctionalInterfaceTypeAccessible(expression, substitute, false);
      }
    }
    else {
      Pair<String, List<IntentionAction>> problem = HighlightUtil.accessProblemDescriptionAndFixes(expression, psiClass, resolveResult);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(problem.first).create();
      if (problem.second != null) {
        problem.second.forEach(fix -> QuickFixAction.registerQuickFixAction(info, fix));
      }
      return true;
    }
    return false;
  }

  @Override
  public void visitReferenceList(PsiReferenceList list) {
    if (list.getFirstChild() == null) return;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiTypeParameter)) {
      
      
      
      
    }
  }

  @Override
  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    if (list.getTextLength() == 0) return;
    
    
     {
      for (PsiTypeElement typeElement : list.getTypeParameterElements()) {
        if (typeElement.getType() instanceof PsiDiamondType) {
        }
      }
    }
  }

  @Override
  public void visitReturnStatement(PsiReturnStatement statement) {
    super.visitStatement(statement);
    if (!myHolder.hasErrorResults() && Feature.ENHANCED_SWITCH.isAvailable(myFile)) {
    }
     {
      try {
        PsiElement parent = PsiTreeUtil.getParentOfType(statement, PsiFile.class, PsiClassInitializer.class,
                                                        PsiLambdaExpression.class, PsiMethod.class);
        HighlightInfo info = parent != null ? HighlightUtil.checkReturnStatementType(statement, parent) : null;
        if (info != null && parent instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)parent;
        }
      }
      catch (IndexNotReadyException ignore) { }
    }
  }

  @Override
  public void visitStatement(PsiStatement statement) {
    super.visitStatement(statement);
    
  }

  @Override
  public void visitSuperExpression(PsiSuperExpression expr) {
     visitExpression(expr);
  }

  @Override
  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    super.visitSwitchLabelStatement(statement);
    
  }

  @Override
  public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
    super.visitSwitchLabeledRuleStatement(statement);
    
  }

  @Override
  public void visitSwitchStatement(PsiSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    checkSwitchBlock(statement);
  }

  @Override
  public void visitSwitchExpression(PsiSwitchExpression expression) {
    super.visitSwitchExpression(expression);
    
    checkSwitchBlock(expression);
    
    
  }

  private void checkSwitchBlock(PsiSwitchBlock switchBlock) {
    
    
    
  }

  @Override
  public void visitThisExpression(PsiThisExpression expr) {
    if (!(expr.getParent() instanceof PsiReceiverParameter)) {
      
       visitExpression(expr);
    }
  }

  @Override
  public void visitThrowStatement(PsiThrowStatement statement) {
     visitStatement(statement);
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    super.visitTryStatement(statement);
     {
      final Set<PsiClassType> thrownTypes = HighlightUtil.collectUnhandledExceptions(statement);
      for (PsiParameter parameter : statement.getCatchBlockParameters()) {
        boolean added = myHolder.addAll(HighlightUtil.checkExceptionAlreadyCaught(parameter));
        if (!added) {
          added = myHolder.addAll(HighlightUtil.checkExceptionThrownInTry(parameter, thrownTypes));
        }
        if (!added) {
        }
      }
    }
  }

  @Override
  public void visitResourceList(PsiResourceList resourceList) {
    super.visitResourceList(resourceList);
    
  }

  @Override
  public void visitResourceVariable(PsiResourceVariable resource) {
    super.visitResourceVariable(resource);
    
    
  }

  @Override
  public void visitResourceExpression(PsiResourceExpression resource) {
    super.visitResourceExpression(resource);
    
    
    
    
  }

  @Override
  public void visitTypeElement(PsiTypeElement type) {
    
    
    
  }

  @Override
  public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
    super.visitTypeCastExpression(typeCast);
    try {
      
      
    }
    catch (IndexNotReadyException ignored) { }
  }

  @Override
  public void visitTypeParameterList(PsiTypeParameterList list) {
    PsiTypeParameter[] typeParameters = list.getTypeParameters();
    if (typeParameters.length > 0) {
      
    }
  }

  @Override
  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);
    try {
      
      
    }
    catch (IndexNotReadyException ignored) { }
  }

  @Override
  public void visitConditionalExpression(PsiConditionalExpression expression) {
    super.visitConditionalExpression(expression);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8) && PsiPolyExpressionUtil.isPolyExpression(expression)) {
      PsiElement element = PsiUtil.skipParenthesizedExprUp(expression.getParent());
       if (element instanceof PsiExpressionList) {
         PsiElement parent = element.getParent();
         if (parent instanceof PsiCall && !((PsiCall)parent).resolveMethodGenerics().isValidResult()) {
          return;
        }
       }
      final PsiExpression thenExpression = expression.getThenExpression();
      final PsiExpression elseExpression = expression.getElseExpression();
      if (thenExpression != null && elseExpression != null) {
        final PsiType conditionalType = expression.getType();
        if (conditionalType != null) {
          final PsiExpression[] sides = {thenExpression, elseExpression};
          for (PsiExpression side : sides) {
            final PsiType sideType = side.getType();
            if (sideType != null && !TypeConversionUtil.isAssignable(conditionalType, sideType)) {
            }
          }
        }
      }
    }
  }

  @Override
  public void visitReceiverParameter(PsiReceiverParameter parameter) {
    super.visitReceiverParameter(parameter);
    
    
    
  }

  @Override
  public void visitModule(PsiJavaModule module) {
    super.visitModule(module);
    
    
    
    
    
    
    
  }

  @Override
  public void visitRequiresStatement(PsiRequiresStatement statement) {
    super.visitRequiresStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      
      if (!myHolder.hasErrorResults() && myLanguageLevel.isAtLeast(LanguageLevel.JDK_10)) {
      }
    }
  }

  @Override
  public void visitPackageAccessibilityStatement(PsiPackageAccessibilityStatement statement) {
    super.visitPackageAccessibilityStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      
      
      
    }
  }

  @Override
  public void visitUsesStatement(PsiUsesStatement statement) {
    super.visitUsesStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      
    }
  }

  @Override
  public void visitProvidesStatement(PsiProvidesStatement statement) {
    super.visitProvidesStatement(statement);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
      
    }
  }

  @Nullable
  private HighlightInfo checkFeature(@NotNull PsiElement element, @NotNull Feature feature) {
    return HighlightUtil.checkFeature(element, feature, myLanguageLevel, myFile);
  }
}