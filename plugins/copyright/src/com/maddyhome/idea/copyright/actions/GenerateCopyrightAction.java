// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.maddyhome.idea.copyright.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenerateCopyrightAction extends AnAction
{
    @Override
    public void update(@NotNull AnActionEvent e)
    {
        Presentation presentation = e.getPresentation();
        DataContext context = e.getDataContext();
        Project project = e.getProject();
        if (project == null)
        {
            presentation.setEnabled(false);
            return;
        }

        PsiFile file = getFile(context, project);
        if (file == null) {
          presentation.setEnabled(false);
        }
    }

    @Nullable
    private static PsiFile getFile(DataContext context, Project project) {
      PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
      if (file == null) {
        Editor editor = CommonDataKeys.EDITOR.getData(context);
        if (editor != null) {
          file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        }
      }
      return file;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
}