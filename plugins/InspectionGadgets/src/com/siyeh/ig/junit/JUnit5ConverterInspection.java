// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public class JUnit5ConverterInspection extends BaseInspection {
  private static final List<String> ruleAnnotations = Arrays.asList(JUnitCommonClassNames.ORG_JUNIT_RULE, JUnitCommonClassNames.ORG_JUNIT_CLASS_RULE);

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit5.converter.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return "#ref can be JUnit 5 test";
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    if (!JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)) return false;
    if (JavaPsiFacade.getInstance(file.getProject()).findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, file.getResolveScope()) == null) {
      return false;
    }
    return super.shouldInspect(file);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MigrateToJUnit5();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {

      @Override
      public void visitClass(PsiClass aClass) {
        TestFramework framework = TestFrameworks.detectFramework(aClass);
        if (framework == null || !"JUnit4".equals(framework.getName())) {
          return;
        }

        if (!canBeConvertedToJUnit5(aClass)) return;

        registerClassError(aClass);
      }
    };
  }

  protected static boolean canBeConvertedToJUnit5(PsiClass aClass) {
    if (AnnotationUtil.isAnnotated(aClass, TestUtils.RUN_WITH, CHECK_HIERARCHY)) {
      return false;
    }

    for (PsiField field : aClass.getAllFields()) {
      if (AnnotationUtil.isAnnotated(field, ruleAnnotations, 0)) {
        return false;
      }
    }

    for (PsiMethod method : aClass.getMethods()) {
      if (AnnotationUtil.isAnnotated(method, ruleAnnotations, 0)) {
        return false;
      }

      PsiAnnotation testAnnotation = AnnotationUtil.findAnnotation(method, true, JUnitCommonClassNames.ORG_JUNIT_TEST);
      if (testAnnotation != null && testAnnotation.getParameterList().getAttributes().length > 0) {
        return false;
      }
    }
    return true;
  }

  private static class MigrateToJUnit5 extends InspectionGadgetsFix implements BatchQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("junit5.converter.fix.name");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project,
                         @NotNull CommonProblemDescriptor[] descriptors,
                         @NotNull List psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
    }
  }
}
