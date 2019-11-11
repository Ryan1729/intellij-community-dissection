/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public abstract class ElementCreator implements WriteActionAware {
  private static final Logger LOG = Logger.getInstance(ElementCreator.class);
  private final Project myProject;
  private final String myErrorTitle;

  protected ElementCreator(Project project, String errorTitle) {
    myProject = project;
    myErrorTitle = errorTitle;
  }

  protected abstract PsiElement[] create(@NotNull String newName) throws Exception;
  protected abstract String getActionName(String newName);

  public PsiElement[] tryCreate(@NotNull final String inputString) {
    if (inputString.isEmpty()) {
      Messages.showMessageDialog(myProject, IdeBundle.message("error.name.should.be.specified"), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return PsiElement.EMPTY_ARRAY;
    }

    Ref<List<SmartPsiElementPointer<?>>> createdElements = Ref.create();
    Exception exception = executeCommand(getActionName(inputString), () -> {
      PsiElement[] psiElements = create(inputString);
      SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
      createdElements.set(ContainerUtil.map(psiElements, manager::createSmartPsiElementPointer));
    });
    if (exception != null) {
      handleException(exception);
      return PsiElement.EMPTY_ARRAY;
    }

    return ContainerUtil.mapNotNull(createdElements.get(), SmartPsiElementPointer::getElement).toArray(PsiElement.EMPTY_ARRAY);
  }

  @Nullable
  private Exception executeCommand(String commandName, ThrowableRunnable<? extends Exception> invokeCreate) {
    final Exception[] exception = new Exception[1];
    return exception[0];
  }

  private void handleException(Exception t) {
    LOG.info(t);
    String errorMessage = getErrorMessage(t);
    Messages.showMessageDialog(myProject, errorMessage, myErrorTitle, Messages.getErrorIcon());
  }

  public static String getErrorMessage(Throwable t) {
    String errorMessage = CreateElementActionBase.filterMessage(t.getMessage());
    if (StringUtil.isEmpty(errorMessage)) {
      errorMessage = t.toString();
    }
    return errorMessage;
  }
}
