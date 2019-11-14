/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ChangeModifierFix;
import com.siyeh.ig.performance.ClassInitializerMayBeStaticInspection;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class ClassInitializerInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnWhenConstructor = false;

  @Override
  @NotNull
  public String getID() {
    return "NonStaticInitializer";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("class.initializer.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("class.initializer.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("class.initializer.option"), this, "onlyWarnWhenConstructor");
  }


  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    PsiClassInitializer classInitializer = (PsiClassInitializer)infos[0];
    final PsiClass aClass = classInitializer.getContainingClass();
    assert aClass != null;
    if (PsiUtil.isInnerClass(aClass) || ClassInitializerMayBeStaticInspection.dependsOnInstanceMembers(classInitializer)) {
      return new InspectionGadgetsFix[] {new MoveToConstructorFix()};
    }
    return new InspectionGadgetsFix[] {
      new ChangeModifierFix(PsiModifier.STATIC),
      new MoveToConstructorFix()
    };
  }

  private static class MoveToConstructorFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("class.initializer.move.code.to.constructor.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement brace = descriptor.getPsiElement();
      final PsiElement parent = brace.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiClassInitializer)) {
        return;
      }
      final PsiClassInitializer initializer = (PsiClassInitializer)grandParent;
      final PsiClass aClass = initializer.getContainingClass();
      if (aClass == null) {
        return;
      }
      final Collection<PsiMethod> constructors = getOrCreateConstructors(aClass);
      for (PsiMethod constructor : constructors) {
        addCodeToMethod(initializer, constructor);
      }
      CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(initializer.getBody());
      tracker.deleteAndRestoreComments(initializer);
    }

    private static void addCodeToMethod(PsiClassInitializer initializer, PsiMethod constructor) {
      final PsiCodeBlock body = constructor.getBody();
      if (body == null) {
        return;
      }
      final PsiCodeBlock codeBlock = initializer.getBody();
      PsiElement element = codeBlock.getFirstBodyElement();
      final PsiElement last = codeBlock.getRBrace();
      while (element != null && element != last) {
        body.add(element);
        element = element.getNextSibling();
      }
    }

    @NotNull
    private static Collection<PsiMethod> getOrCreateConstructors(@NotNull PsiClass aClass) {
      PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 0) {
        final IntentionAction addDefaultConstructorFix = QuickFixFactory.getInstance().createAddDefaultConstructorFix(aClass);
        addDefaultConstructorFix.invoke(aClass.getProject(), null, aClass.getContainingFile());
      }
      constructors = aClass.getConstructors();
      return removeChainedConstructors(ContainerUtil.newArrayList(constructors));
    }

    @NotNull
    private static Collection<PsiMethod> removeChainedConstructors(@NotNull Collection<PsiMethod> constructors) {
      return constructors;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassInitializerVisitor();
  }

  private class ClassInitializerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      super.visitClassInitializer(initializer);
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass aClass =  initializer.getContainingClass();
      if (aClass == null || aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (onlyWarnWhenConstructor && aClass.getConstructors().length == 0) {
        return;
      }
      registerClassInitializerError(initializer, initializer);
    }
  }
}