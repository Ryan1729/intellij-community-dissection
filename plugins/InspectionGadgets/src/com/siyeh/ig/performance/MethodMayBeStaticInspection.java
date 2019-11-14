/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MethodMayBeStaticInspection extends BaseInspection {
  protected static final String IGNORE_DEFAULT_METHODS_ATTR_NAME = "m_ignoreDefaultMethods";
  protected static final String ONLY_PRIVATE_OR_FINAL_ATTR_NAME = "m_onlyPrivateOrFinal";
  protected static final String IGNORE_EMPTY_METHODS_ATTR_NAME = "m_ignoreEmptyMethods";
  protected static final String REPLACE_QUALIFIER_ATTR_NAME = "m_replaceQualifier";
  /**
   * @noinspection PublicField
   */
  public boolean m_onlyPrivateOrFinal = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreEmptyMethods = true;
  public boolean m_ignoreDefaultMethods = true;
  public boolean m_replaceQualifier = true;

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new InspectionGadgetsFix() {
      @Override
      public void doFix(Project project, ProblemDescriptor descriptor) {
        PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return InspectionGadgetsBundle.message("change.modifier.quickfix", PsiModifier.STATIC);
      }
    };
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("method.may.be.static.only.option"), ONLY_PRIVATE_OR_FINAL_ATTR_NAME);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("method.may.be.static.empty.option"), IGNORE_EMPTY_METHODS_ATTR_NAME);
    optionsPanel.addCheckbox("Ignore 'default' methods", IGNORE_DEFAULT_METHODS_ATTR_NAME);
    optionsPanel.addCheckbox("Quick fix replaces instance qualifiers with class references", REPLACE_QUALIFIER_ATTR_NAME);
    return optionsPanel;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.may.be.static.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.may.be.static.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCanBeStaticVisitor();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    node.addContent(new Element("option").setAttribute("name", ONLY_PRIVATE_OR_FINAL_ATTR_NAME).setAttribute("value", String.valueOf(m_onlyPrivateOrFinal)));
    node.addContent(new Element("option").setAttribute("name", IGNORE_EMPTY_METHODS_ATTR_NAME).setAttribute("value", String.valueOf(
      m_ignoreEmptyMethods)));
    if (!m_ignoreDefaultMethods) {
      node.addContent(new Element("option").setAttribute("name", IGNORE_DEFAULT_METHODS_ATTR_NAME).setAttribute("value", "false"));
    }
    if (!m_replaceQualifier) {
      node.addContent(new Element("option").setAttribute("name", REPLACE_QUALIFIER_ATTR_NAME).setAttribute("value", "false"));
    }
  }

  private class MethodCanBeStaticVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (method.hasModifierProperty(PsiModifier.STATIC) ||
          method.hasModifierProperty(PsiModifier.ABSTRACT) ||
          method.hasModifierProperty(PsiModifier.SYNCHRONIZED) ||
          method.hasModifierProperty(PsiModifier.NATIVE)) {
        return;
      }
      if (method.isConstructor() || method.getNameIdentifier() == null) {
        return;
      }
      if (m_ignoreDefaultMethods && method.hasModifierProperty(PsiModifier.DEFAULT)) {
        return;
      }
      if (m_ignoreEmptyMethods && MethodUtils.isEmpty(method)) {
        return;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(method);
      if (containingClass == null) {
        return;
      }
      final Condition<PsiElement>[] addins = InspectionManager.CANT_BE_STATIC_EXTENSION.getExtensions();
      for (Condition<PsiElement> addin : addins) {
        if (addin.value(method)) {
          return;
        }
      }
      final PsiElement scope = containingClass.getScope();
      if (!(scope instanceof PsiJavaFile) && !containingClass.hasModifierProperty(PsiModifier.STATIC) && !containingClass.isInterface()) {
        return;
      }
      if (m_onlyPrivateOrFinal && !method.hasModifierProperty(PsiModifier.FINAL) && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (isExcluded(method) || MethodUtils.hasSuper(method) || MethodUtils.isOverridden(method)) {
        return;
      }
      if (FindSuperElementsHelper.getSiblingInheritedViaSubClass(method) != null) {
        return;
      }
      final MethodReferenceVisitor visitor = new MethodReferenceVisitor(method);
      method.accept(visitor);
      if (!visitor.areReferencesStaticallyAccessible()) {
        return;
      }
      registerMethodError(method);
    }

    private boolean isExcluded(PsiMethod method) {
      return SerializationUtils.isWriteObject(method) || SerializationUtils.isReadObject(method) ||
             SerializationUtils.isWriteReplace(method) || SerializationUtils.isReadResolve(method);
    }
  }
}
