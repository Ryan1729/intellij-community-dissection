// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.psi.util.*;
import com.intellij.refactoring.*;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.NotInSuperCallOccurrenceFilter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

/**
 * @author dsl
 */
public abstract class IntroduceVariableBase extends IntroduceHandlerBase {
  public static class JavaReplaceChoice implements OccurrencesChooser.BaseReplaceChoice {
    public static final JavaReplaceChoice NO = new JavaReplaceChoice(OccurrencesChooser.ReplaceChoice.NO);
    public static final JavaReplaceChoice ALL = new JavaReplaceChoice(OccurrencesChooser.ReplaceChoice.ALL);

    private final String myDescription;
    private final boolean myAll, myMultiple, myChain;

    JavaReplaceChoice(OccurrencesChooser.ReplaceChoice choice) {
      this(choice.getDescription(), choice.isAll(), choice.isMultiple(), false);
    }

    JavaReplaceChoice(String description, boolean all, boolean multiple, boolean chain) {
      myDescription = description;
      myAll = all;
      myMultiple = multiple;
      myChain = chain;
    }

    public String getDescription() {
      return myDescription;
    }

    @Override
    public boolean isMultiple() {
      return myMultiple;
    }

    @Override
    public boolean isAll() {
      return myAll;
    }

    public boolean isChain() {
      return myChain;
    }

    @Override
    public String formatDescription(int occurrencesCount) {
      return MessageFormat.format(getDescription(), occurrencesCount);
    }
  }

  private static final Logger LOG = Logger.getInstance(IntroduceVariableBase.class);
  @NonNls private static final String PREFER_STATEMENTS_OPTION = "introduce.variable.prefer.statements";

  protected static final String REFACTORING_NAME = RefactoringBundle.message("introduce.variable.title");
  public static final Key<Boolean> NEED_PARENTHESIS = Key.create("NEED_PARENTHESIS");

  public static SuggestedNameInfo getSuggestedName(@Nullable PsiType type, @NotNull final PsiExpression expression) {
    return getSuggestedName(type, expression, expression);
  }

  public static SuggestedNameInfo getSuggestedName(@Nullable PsiType type,
                                                   @NotNull final PsiExpression expression,
                                                   final PsiElement anchor) {
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
    final SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expression, type);
    final String[] strings = JavaCompletionUtil
      .completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
    final SuggestedNameInfo.Delegate delegate = new SuggestedNameInfo.Delegate(strings, nameInfo);
    return codeStyleManager.suggestUniqueVariableName(delegate, anchor, true);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement[] statementsInRange = findStatementsAtOffset(editor, file, offset);

      //try line selection
      if (statementsInRange.length == 1 && selectLineAtCaret(offset, statementsInRange)) {
        selectionModel.selectLineAtCaret();
        final PsiExpression expressionInRange =
          findExpressionInRange(project, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        if (expressionInRange == null || getErrorMessage(expressionInRange) != null) {
          selectionModel.removeSelection();
        }
      }

      if (!selectionModel.hasSelection()) {
        final List<PsiExpression> expressions = ContainerUtil
          .filter(collectExpressions(file, editor, offset), expression ->
            PsiTreeUtil.getParentOfType(expression, PsiField.class, true, PsiStatement.class) != null);
        if (expressions.isEmpty()) {
          selectionModel.selectLineAtCaret();
        } else if (!isChooserNeeded(expressions)) {
          final TextRange textRange = expressions.get(0).getTextRange();
          selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }
        else {
          IntroduceTargetChooser.showChooser(editor, expressions,
            new Pass<PsiExpression>(){
              @Override
              public void pass(final PsiExpression selectedValue) {
                invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
              }
            },
            new PsiExpressionTrimRenderer.RenderFunction(), "Expressions", preferredSelection(statementsInRange, expressions), ScopeHighlighter.NATURAL_RANGER);
          return;
        }
      }
    }
    if (invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()) &&
        LookupManager.getActiveLookup(editor) == null) {
      selectionModel.removeSelection();
    }
  }

  public static boolean isChooserNeeded(List<? extends PsiExpression> expressions) {
    if (expressions.size() == 1) {
      final PsiExpression expression = expressions.get(0);
      return expression instanceof PsiNewExpression && ((PsiNewExpression)expression).getAnonymousClass() != null;
    }
    return true;
  }

  public static boolean selectLineAtCaret(int offset, PsiElement[] statementsInRange) {
    TextRange range = statementsInRange[0].getTextRange();
    if (statementsInRange[0] instanceof PsiExpressionStatement) {
      range = ((PsiExpressionStatement)statementsInRange[0]).getExpression().getTextRange();
    }

    return range.getStartOffset() > offset ||
           range.getEndOffset() <= offset ||
           isPreferStatements();
  }

  public static int preferredSelection(PsiElement[] statementsInRange, List<? extends PsiExpression> expressions) {
    int selection;
    if (statementsInRange.length == 1 &&
        statementsInRange[0] instanceof PsiExpressionStatement &&
        PsiUtilCore.hasErrorElementChild(statementsInRange[0])) {
      selection = expressions.indexOf(((PsiExpressionStatement)statementsInRange[0]).getExpression());
    } else {
      PsiExpression expression = expressions.get(0);
      if (expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).resolve() instanceof PsiLocalVariable) {
        selection = 1;
      }
      else {
        selection = -1;
      }
    }
    return selection;
  }

  public static boolean isPreferStatements() {
    return Boolean.valueOf(PropertiesComponent.getInstance().getBoolean(PREFER_STATEMENTS_OPTION)) || Registry.is(PREFER_STATEMENTS_OPTION, false);
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Editor editor,
                                                       final int offset) {
    return collectExpressions(file, editor, offset, false);
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Editor editor,
                                                       final int offset,
                                                       boolean acceptVoid) {
    return collectExpressions(file, editor.getDocument(), offset, acceptVoid);
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Document document,
                                                       final int offset,
                                                       boolean acceptVoid) {
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
      correctedOffset--;
    }
    if (correctedOffset < 0) {
      correctedOffset = offset;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(correctedOffset))) {
      if (text.charAt(correctedOffset) == ';') {//initially caret on the end of line
        correctedOffset--;
      }
      if (correctedOffset < 0 || text.charAt(correctedOffset) != ')' && text.charAt(correctedOffset) != '.') {
        correctedOffset = offset;
      }
    }
    final PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    final List<PsiExpression> expressions = new ArrayList<>();
    /*for (PsiElement element : statementsInRange) {
      if (element instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
        if (expression.getType() != PsiType.VOID) {
          expressions.add(expression);
        }
      }
    }*/
    PsiExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    while (expression != null) {
      if (!expressions.contains(expression) && !(expression instanceof PsiParenthesizedExpression) && !(expression instanceof PsiSuperExpression) &&
          (acceptVoid || !PsiType.VOID.equals(expression.getType()))) {
        if (expression instanceof PsiMethodReferenceExpression) {
          expressions.add(expression);
        }
        else if (!(expression instanceof PsiAssignmentExpression)) {
          if (!(expression instanceof PsiReferenceExpression)) {
            expressions.add(expression);
          }
          else {
            if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
              final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
              if (!(resolve instanceof PsiClass) && !(resolve instanceof PsiPackage)) {
                expressions.add(expression);
              }
            }
          }
        }
      }
      expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
    }
    return expressions;
  }

  public static PsiElement[] findStatementsAtOffset(final Editor editor, final PsiFile file, final int offset) {
    final Document document = editor.getDocument();
    final int lineNumber = document.getLineNumber(offset);
    final int lineStart = document.getLineStartOffset(lineNumber);
    final int lineEnd = document.getLineEndOffset(lineNumber);

    return CodeInsightUtil.findStatementsInRange(file, lineStart, lineEnd);
  }

  private boolean invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.REFACTORING_INTRODUCE_VARIABLE);
    PsiDocumentManager.getInstance(project).commitAllDocuments();


    return invokeImpl(project, findExpressionInRange(project, file, startOffset, endOffset), editor);
  }

  private static PsiExpression findExpressionInRange(Project project, PsiFile file, int startOffset, int endOffset) {
    PsiExpression tempExpr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (tempExpr == null) {
      PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (statements.length == 1) {
        if (statements[0] instanceof PsiExpressionStatement) {
          tempExpr = ((PsiExpressionStatement) statements[0]).getExpression();
        }
        else if (statements[0] instanceof PsiReturnStatement) {
          tempExpr = ((PsiReturnStatement)statements[0]).getReturnValue();
        }
        else if (statements[0] instanceof PsiSwitchStatement) {
          PsiExpression expr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(statements[0].getText(), statements[0]);
          TextRange range = statements[0].getTextRange();
          final RangeMarker rangeMarker = FileDocumentManager.getInstance().getDocument(file.getVirtualFile()).createRangeMarker(range);
          expr.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);
          expr.putUserData(ElementToWorkOn.PARENT, statements[0]);
          return expr;
        }
      }
    }

    if (tempExpr == null) {
      tempExpr = getSelectedExpression(project, file, startOffset, endOffset);
    }
    return tempExpr;
  }

  /**
   * @return can return NotNull value although extraction will fail: reason could be retrieved from {@link #getErrorMessage(PsiExpression)}
   */
  public static PsiExpression getSelectedExpression(final Project project, PsiFile file, int startOffset, int endOffset) {
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    PsiElement elementAtStart = file.findElementAt(startOffset);
    if (elementAtStart == null || elementAtStart instanceof PsiWhiteSpace || elementAtStart instanceof PsiComment) {
      final PsiElement element = PsiTreeUtil.skipWhitespacesAndCommentsForward(elementAtStart);
      if (element != null) {
        startOffset = element.getTextOffset();
        elementAtStart = file.findElementAt(startOffset);
      }
      if (elementAtStart == null) {
        if (injectedLanguageManager.isInjectedFragment(file)) {
          return getSelectionFromInjectedHost(project, file, injectedLanguageManager, startOffset, endOffset);
        } else {
          return null;
        }
      }
      startOffset = elementAtStart.getTextOffset();
    }
    PsiElement elementAtEnd = file.findElementAt(endOffset - 1);
    if (elementAtEnd == null || elementAtEnd instanceof PsiWhiteSpace || elementAtEnd instanceof PsiComment) {
      elementAtEnd = PsiTreeUtil.skipWhitespacesAndCommentsBackward(elementAtEnd);
      if (elementAtEnd == null) return null;
      endOffset = elementAtEnd.getTextRange().getEndOffset();
    }

    if (endOffset <= startOffset) return null;

    PsiElement elementAt = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
    if (elementAt instanceof PsiExpressionStatement) {
      return ((PsiExpressionStatement)elementAt).getExpression();
    }
    final PsiExpression containingExpression = PsiTreeUtil.getParentOfType(elementAt, PsiExpression.class, false);

    if (containingExpression != null && containingExpression == elementAtEnd && startOffset == containingExpression.getTextOffset()) {
      return containingExpression;
    }

    if (containingExpression == null || containingExpression instanceof PsiLambdaExpression) {
      if (injectedLanguageManager.isInjectedFragment(file)) {
        return getSelectionFromInjectedHost(project, file, injectedLanguageManager, startOffset, endOffset);
      }
      elementAt = null;
    }
    final PsiLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(elementAt, PsiLiteralExpression.class);

    final PsiLiteralExpression startLiteralExpression = PsiTreeUtil.getParentOfType(elementAtStart, PsiLiteralExpression.class);
    final PsiLiteralExpression endLiteralExpression = PsiTreeUtil.getParentOfType(file.findElementAt(endOffset), PsiLiteralExpression.class);

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    String text = null;
    PsiExpression tempExpr;
    try {
      text = file.getText().subSequence(startOffset, endOffset).toString();
      String prefix = null;
      if (startLiteralExpression != null) {
        final int startExpressionOffset = startLiteralExpression.getTextOffset();
        if (startOffset == startExpressionOffset + 1) {
          text = "\"" + text;
        } else if (startOffset > startExpressionOffset + 1){
          prefix = "\" + ";
          text = "\"" + text;
        }
      }

      String suffix = null;
      if (endLiteralExpression != null) {
        final int endExpressionOffset = endLiteralExpression.getTextOffset() + endLiteralExpression.getTextLength();
        if (endOffset == endExpressionOffset - 1) {
          text += "\"";
        } else if (endOffset < endExpressionOffset - 1) {
          suffix = " + \"";
          text += "\"";
        }
      }

      if (literalExpression != null && text.equals(literalExpression.getText())) return literalExpression;

      final PsiElement parent = literalExpression != null ? literalExpression : elementAt;
      tempExpr = elementFactory.createExpressionFromText(text, parent);

      final boolean [] hasErrors = new boolean[1];
      final JavaRecursiveElementWalkingVisitor errorsVisitor = new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(final PsiElement element) {
          if (hasErrors[0]) {
            return;
          }
          super.visitElement(element);
        }

        @Override
        public void visitErrorElement(final PsiErrorElement element) {
          hasErrors[0] = true;
        }
      };
      tempExpr.accept(errorsVisitor);
      if (hasErrors[0]) return null;

      tempExpr.putUserData(ElementToWorkOn.PREFIX, prefix);
      tempExpr.putUserData(ElementToWorkOn.SUFFIX, suffix);

      final RangeMarker rangeMarker =
        FileDocumentManager.getInstance().getDocument(file.getVirtualFile()).createRangeMarker(startOffset, endOffset);
      tempExpr.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);

      if (parent != null) {
        tempExpr.putUserData(ElementToWorkOn.PARENT, parent);
      }
      else {
        PsiErrorElement errorElement = elementAtStart instanceof PsiErrorElement
                                       ? (PsiErrorElement)elementAtStart
                                       : PsiTreeUtil.getNextSiblingOfType(elementAtStart, PsiErrorElement.class);
        if (errorElement == null) {
          errorElement = PsiTreeUtil.getParentOfType(elementAtStart, PsiErrorElement.class);
        }
        if (errorElement == null) return null;
        if (!(errorElement.getParent() instanceof PsiClass)) return null;
        tempExpr.putUserData(ElementToWorkOn.PARENT, errorElement);
        tempExpr.putUserData(ElementToWorkOn.OUT_OF_CODE_BLOCK, Boolean.TRUE);
      }

      final String fakeInitializer = "intellijidearulezzz";
      final int[] refIdx = new int[1];
      final PsiElement toBeExpression = createReplacement(fakeInitializer, project, prefix, suffix, parent, rangeMarker, refIdx);
      toBeExpression.accept(errorsVisitor);
      if (hasErrors[0]) return null;
      if (literalExpression != null && toBeExpression instanceof PsiExpression) {
        PsiType type = ((PsiExpression)toBeExpression).getType();
        if (type != null && !type.equals(literalExpression.getType())) {
          return null;
        }
      }
      else if (containingExpression != null) {
        PsiType containingExpressionType = containingExpression.getType();
        PsiType tempExprType = tempExpr.getType();
        if (containingExpressionType != null && 
            (tempExprType == null || !TypeConversionUtil.isAssignable(containingExpressionType, tempExprType))) {
          return null;
        }
      }

      final PsiReferenceExpression refExpr = PsiTreeUtil.getParentOfType(toBeExpression.findElementAt(refIdx[0]), PsiReferenceExpression.class);
      if (refExpr == null) return null;
      if (toBeExpression == refExpr && refIdx[0] > 0) {
        return null;
      }
      if (ReplaceExpressionUtil.isNeedParenthesis(refExpr.getNode(), tempExpr.getNode())) {
        tempExpr.putCopyableUserData(NEED_PARENTHESIS, Boolean.TRUE);
        return tempExpr;
      }
    }
    catch (IncorrectOperationException e) {
      if (elementAt instanceof PsiExpressionList) {
        final PsiElement parent = elementAt.getParent();
        return parent instanceof PsiCallExpression ? createArrayCreationExpression(text, startOffset, endOffset, (PsiCallExpression)parent) : null;
      }
      return null;
    }

    return tempExpr;
  }

  private static PsiExpression getSelectionFromInjectedHost(Project project,
                                                            PsiFile file,
                                                            InjectedLanguageManager injectedLanguageManager, int startOffset, int endOffset) {
    final PsiLanguageInjectionHost injectionHost = injectedLanguageManager.getInjectionHost(file);
    return getSelectedExpression(project, injectionHost.getContainingFile(), injectedLanguageManager.injectedToHost(file, startOffset), injectedLanguageManager.injectedToHost(file, endOffset));
  }

  @Nullable
  public static String getErrorMessage(PsiExpression expr) {
    final Boolean needParenthesis = expr.getCopyableUserData(NEED_PARENTHESIS);
    if (needParenthesis != null && needParenthesis.booleanValue()) {
      return "Extracting selected expression would change the semantic of the whole expression.";
    }
    return null;
  }

  private static PsiExpression createArrayCreationExpression(String text, int startOffset, int endOffset, PsiCallExpression parent) {
    if (text == null || parent == null) return null;
    if (text.contains(",")) {
      PsiExpressionList argumentList = parent.getArgumentList();
      assert argumentList != null; // checked at call site
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parent.getProject());
      final JavaResolveResult resolveResult = parent.resolveMethodGenerics();
      final PsiMethod psiMethod = (PsiMethod)resolveResult.getElement();
      if (psiMethod == null || !psiMethod.isVarArgs()) return null;
      final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      final PsiParameter varargParameter = parameters[parameters.length - 1];
      final PsiType type = varargParameter.getType();
      LOG.assertTrue(type instanceof PsiEllipsisType);
      final PsiArrayType psiType = (PsiArrayType)((PsiEllipsisType)type).toArrayType();
      final PsiExpression[] args = argumentList.getExpressions();
      final PsiSubstitutor psiSubstitutor = resolveResult.getSubstitutor();

      if (args.length < parameters.length || startOffset < args[parameters.length - 1].getTextRange().getStartOffset()) return null;

      final PsiFile containingFile = parent.getContainingFile();

      PsiElement startElement = containingFile.findElementAt(startOffset);
      while (startElement != null && startElement.getParent() != argumentList) {
        startElement = startElement.getParent();
      }
      if (!(startElement instanceof PsiExpression) || startOffset > startElement.getTextOffset()) return null;

      PsiElement endElement = containingFile.findElementAt(endOffset - 1);
      while (endElement != null && endElement.getParent() != argumentList) {
        endElement = endElement.getParent();
      }
      if (!(endElement instanceof PsiExpression) || endOffset < endElement.getTextRange().getEndOffset()) return null;

      final PsiType componentType = TypeConversionUtil.erasure(psiSubstitutor.substitute(psiType.getComponentType()));
      try {
        final PsiExpression expressionFromText =
          elementFactory.createExpressionFromText("new " + componentType.getCanonicalText() + "[]{" + text + "}", parent);
        final RangeMarker rangeMarker =
        FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile()).createRangeMarker(startOffset, endOffset);
        expressionFromText.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);
        expressionFromText.putUserData(ElementToWorkOn.PARENT, parent);
        return expressionFromText;
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }
    return null;
  }

  @Override
  protected boolean invokeImpl(final Project project, final PsiExpression expr,
                               final Editor editor) {
    if (expr != null) {
      final String errorMessage = getErrorMessage(expr);
      if (errorMessage != null) {
        showErrorMessage(project, editor, RefactoringBundle.getCannotRefactorMessage(errorMessage));
        return false;
      }
    }

    if (expr != null && expr.getParent() instanceof PsiExpressionStatement) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable.incompleteStatement");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    if (expr == null || !expr.isPhysical()) {
      if (expr == null) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.an.expression"));
        showErrorMessage(project, editor, message);
        return false;
      }
    }

    String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("unknown.expression.type"));
    showErrorMessage(project, editor, message);
    return false;
  }

  public static boolean canBeExtractedWithoutExplicitType(PsiExpression expr) {
    if (PsiUtil.isLanguageLevel10OrHigher(expr)) {
      PsiType type = expr.getType();
      return type != null &&
             !PsiType.NULL.equals(type) &&
             PsiTypesUtil.isDenotableType(type, expr) &&
             (expr instanceof PsiNewExpression || type.equals(((PsiExpression)expr.copy()).getType()));
    }
    return false;
  }

  @Contract("_, _, null -> null")
  protected PsiElement checkAnchorStatement(Project project, Editor editor, PsiElement anchorStatement) {
    if (anchorStatement == null) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
      showErrorMessage(project, editor, message);
      return null;
    }
    if (checkAnchorBeforeThisOrSuper(project, editor, anchorStatement, REFACTORING_NAME, "HelpID.INTRODUCE_VARIABLE")) return null;

    final PsiElement tempContainer = anchorStatement.getParent();

    if (!(tempContainer instanceof PsiCodeBlock) &&
        !(tempContainer instanceof PsiLambdaExpression) &&
        (tempContainer.getParent() instanceof PsiLambdaExpression)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
      showErrorMessage(project, editor, message);
      return null;
    }
    return tempContainer;
  }

  protected JavaReplaceChoice getOccurrencesChoice() {
    return null;
  }

  protected static PsiElement chooseAnchor(boolean allOccurrences,
                                           boolean hasWriteAccess,
                                           List<PsiExpression> nonWrite,
                                           PsiElement anchorStatementIfAll,
                                           PsiElement anchorStatement) {
    if (allOccurrences) {
      if (hasWriteAccess) {
        return null;
      }
      else {
        return anchorStatementIfAll;
      }
    }
    else {
      return anchorStatement;
    }
  }

  protected boolean isInplaceAvailableInTestMode() {
    return false;
  }

  private static ExpressionOccurrenceManager createOccurrenceManager(PsiExpression expr, PsiElement tempContainer) {
    Set<PsiVariable> vars = new HashSet<>();
    SyntaxTraverser.psiTraverser().withRoot(expr)
      .filter(element -> element instanceof PsiReferenceExpression)
      .forEach(element -> {
        final PsiElement resolve = ((PsiReferenceExpression)element).resolve();
        if (resolve instanceof PsiVariable) {
          vars.add((PsiVariable)resolve);
        }
      });

    PsiElement containerParent = tempContainer;
    PsiElement lastScope = tempContainer;
    while (true) {
      if (containerParent instanceof PsiFile) break;
      if (containerParent instanceof PsiMethod) {
        PsiClass containingClass = ((PsiMethod)containerParent).getContainingClass();
        if (containingClass == null || !PsiUtil.isLocalOrAnonymousClass(containingClass)) break;
        if (vars.stream().anyMatch(variable -> PsiTreeUtil.isAncestor(containingClass, variable, true))) {
          break;
        }
      }
      if (containerParent instanceof PsiLambdaExpression) {
        PsiParameter[] parameters = ((PsiLambdaExpression)containerParent).getParameterList().getParameters();
        if (Arrays.stream(parameters).anyMatch(vars::contains)) {
          break;
        }
      }
      if (containerParent instanceof PsiForStatement) {
        PsiForStatement forStatement = (PsiForStatement)containerParent;
        if (vars.stream().anyMatch(variable -> PsiTreeUtil.isAncestor(forStatement.getInitialization(), variable, true))) {
          break;
        }
      }
      containerParent = containerParent.getParent();
      if (containerParent instanceof PsiCodeBlock) {
        lastScope = containerParent;
      }
    }
    return new ExpressionOccurrenceManager(expr, lastScope, NotInSuperCallOccurrenceFilter.INSTANCE);
  }

  private static boolean isInJspHolderMethod(PsiExpression expr) {
    final PsiElement parent1 = expr.getParent();
    if (parent1 == null) {
      return false;
    }
    final PsiElement parent2 = parent1.getParent();
    if (!(parent2 instanceof JspCodeBlock)) return false;
    final PsiElement parent3 = parent2.getParent();
    return parent3 instanceof JspHolderMethod;
  }

  static boolean isFinalVariableOnLHS(PsiExpression expr) {
    return false;
  }

  public static PsiExpression simplifyVariableInitializer(final PsiExpression initializer,
                                                        final PsiType expectedType) {
    return simplifyVariableInitializer(initializer, expectedType, true);
  }

  public static PsiExpression simplifyVariableInitializer(final PsiExpression initializer,
                                                          final PsiType expectedType,
                                                          final boolean inDeclaration) {

    if (initializer instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)initializer).getOperand();
      if (operand != null) {
        PsiType operandType = operand.getType();
        if (operandType != null && TypeConversionUtil.isAssignable(expectedType, operandType)) {
          return operand;
        }
      }
    }
    else if (initializer instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)initializer;
      if (newExpression.getArrayInitializer() != null) {
        if (inDeclaration) {
          return newExpression.getArrayInitializer();
        }
      }
      else {
        PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
        if (ref != null) {
          final PsiExpression tryToDetectDiamondNewExpr = ((PsiVariable)JavaPsiFacade.getElementFactory(initializer.getProject())
            .createVariableDeclarationStatement("x", expectedType, initializer, initializer).getDeclaredElements()[0])
            .getInitializer();
          if (tryToDetectDiamondNewExpr instanceof PsiNewExpression &&
              PsiDiamondTypeUtil.canCollapseToDiamond((PsiNewExpression)tryToDetectDiamondNewExpr,
                                                      (PsiNewExpression)tryToDetectDiamondNewExpr,
                                                      expectedType)) {
            final PsiElement paramList = RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(ref.getParameterList());
            return PsiTreeUtil.getParentOfType(paramList, PsiNewExpression.class);
          }
        }
      }
    }
    return initializer;
  }

  /**
   * Ensure that diamond inside initializer is expanded, then replace variable type with var
   */
  public static PsiElement expandDiamondsAndReplaceExplicitTypeWithVar(PsiTypeElement typeElement, PsiElement context) {
    PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiVariable) {
      PsiExpression copyVariableInitializer = ((PsiVariable)parent).getInitializer();
      if (copyVariableInitializer instanceof PsiNewExpression) {
        final PsiDiamondType.DiamondInferenceResult diamondResolveResult =
          PsiDiamondTypeImpl.resolveInferredTypesNoCheck((PsiNewExpression)copyVariableInitializer, copyVariableInitializer);
        if (!diamondResolveResult.getInferredTypes().isEmpty()) {
          PsiDiamondTypeUtil.expandTopLevelDiamondsInside(copyVariableInitializer);
        }
      }
    }

    return typeElement.replace(JavaPsiFacade.getElementFactory(context.getProject()).createTypeElementFromText("var", context));
  }

  public static PsiElement replace(final PsiExpression expr1, final PsiExpression ref, final Project project)
    throws IncorrectOperationException {
    final PsiExpression expr2;
    if (expr1 instanceof PsiArrayInitializerExpression &&
      expr1.getParent() instanceof PsiNewExpression) {
      expr2 = (PsiNewExpression) expr1.getParent();
    } else {
      expr2 = null;
    }
    if (expr2.isPhysical() || expr1.getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL) != null) {
      return expr2.replace(ref);
    }
    else {
      final String prefix  = expr1.getUserData(ElementToWorkOn.PREFIX);
      final String suffix  = expr1.getUserData(ElementToWorkOn.SUFFIX);
      final PsiElement parent = expr1.getUserData(ElementToWorkOn.PARENT);
      final RangeMarker rangeMarker = expr1.getUserData(ElementToWorkOn.TEXT_RANGE);

      LOG.assertTrue(parent != null, expr1);
      return parent.replace(createReplacement(ref.getText(), project, prefix, suffix, parent, rangeMarker, new int[1]));
    }
  }

  private static PsiElement createReplacement(final String refText, final Project project,
                                                 final String prefix,
                                                 final String suffix,
                                                 final PsiElement parent, final RangeMarker rangeMarker, int[] refIdx) {
    String text = refText;
    if (parent != null) {
      final String allText = parent.getContainingFile().getText();
      final TextRange parentRange = parent.getTextRange();

      LOG.assertTrue(parentRange.getStartOffset() <= rangeMarker.getStartOffset(), parent + "; prefix:" + prefix + "; suffix:" + suffix);
      String beg = allText.substring(parentRange.getStartOffset(), rangeMarker.getStartOffset());
      //noinspection SSBasedInspection (suggested replacement breaks behavior)
      if (StringUtil.stripQuotesAroundValue(beg).trim().isEmpty() && prefix == null) beg = "";

      LOG.assertTrue(rangeMarker.getEndOffset() <= parentRange.getEndOffset(), parent + "; prefix:" + prefix + "; suffix:" + suffix);
      String end = allText.substring(rangeMarker.getEndOffset(), parentRange.getEndOffset());
      //noinspection SSBasedInspection (suggested replacement breaks behavior)
      if (StringUtil.stripQuotesAroundValue(end).trim().isEmpty() && suffix == null) end = "";

      final String start = beg + (prefix != null ? prefix : "");
      refIdx[0] = start.length();
      text = start + refText + (suffix != null ? suffix : "") + end;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    return parent instanceof PsiStatement ? factory.createStatementFromText(text, parent) :
                                            parent instanceof PsiCodeBlock ? factory.createCodeBlockFromText(text, parent)
                                                                           : factory.createExpressionFromText(text, parent);
  }

  @Override
  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    throw new UnsupportedOperationException();
  }

  protected static void highlightReplacedOccurrences(Project project, Editor editor, PsiElement[] replacedOccurrences){
    if (editor == null) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlights(editor, replacedOccurrences, attributes, true, null);
    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }

  protected abstract void showErrorMessage(Project project, Editor editor, String message);


  public Object getSettings(Project project, Editor editor,
                                               PsiExpression expr, PsiExpression[] occurrences,
                                               boolean declareFinalIfAll,
                                               boolean anyAssignmentLHS,
                                               PsiElement anchor,
                                               final JavaReplaceChoice replaceChoice) {
    final boolean replaceAll = replaceChoice.isMultiple();
    final SuggestedNameInfo suggestedName = getSuggestedName(null, expr, anchor);
    return null;
  }

  public static boolean checkAnchorBeforeThisOrSuper(final Project project,
                                                     final Editor editor,
                                                     final PsiElement tempAnchorElement,
                                                     final String refactoringName,
                                                     final String helpID) {
    if (tempAnchorElement instanceof PsiExpressionStatement) {
      PsiExpression enclosingExpr = ((PsiExpressionStatement)tempAnchorElement).getExpression();
      if (enclosingExpr instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)enclosingExpr).resolveMethod();
        if (method != null && method.isConstructor()) {
          //This is either 'this' or 'super', both must be the first in the respective constructor
          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invalid.expression.context"));
          CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpID);
          return true;
        }
      }
    }
    return false;
  }

  public static void checkInLoopCondition(PsiExpression occurence, MultiMap<PsiElement, String> conflicts) {
    return;
  }

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return null;
  }
}
