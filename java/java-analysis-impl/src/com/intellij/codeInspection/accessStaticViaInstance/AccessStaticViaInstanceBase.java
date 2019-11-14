// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.codeInspection.accessStaticViaInstance;

import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AccessStaticViaInstanceBase extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @NonNls public static final String ACCESS_STATIC_VIA_INSTANCE = "AccessStaticViaInstance";

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("access.static.via.instance");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return ACCESS_STATIC_VIA_INSTANCE;
  }

  @Override
  public String getAlternativeID() {
    return "static-access";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        checkAccessStaticMemberViaInstanceReference(expression, holder, isOnTheFly);
      }
    };
  }

  private void checkAccessStaticMemberViaInstanceReference(PsiReferenceExpression expr, ProblemsHolder holder, boolean onTheFly) {
    JavaResolveResult result = expr.advancedResolve(false);
    PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiMember)) return;
    PsiExpression qualifierExpression = expr.getQualifierExpression();
    if (qualifierExpression == null) return;

    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement qualifierResolved = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) {
        return;
      }
    }
    if (!((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) return;

    //don't report warnings on compilation errors
    PsiClass containingClass = ((PsiMember)resolved).getContainingClass();
    if (containingClass != null && containingClass.isInterface()) return;

    String description = "String description";
    holder.registerProblem(expr, description, createAccessStaticViaInstanceFix(expr, onTheFly, result));
  }

  protected LocalQuickFix createAccessStaticViaInstanceFix(PsiReferenceExpression expr,
                                                           boolean onTheFly,
                                                           JavaResolveResult result) {
    return null;
  }
}
