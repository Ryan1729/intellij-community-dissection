// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

public class UpdateCopyrightProcessor extends AbstractFileProcessor
{
    public static final String TITLE = "Update Copyright";
    public static final String MESSAGE = "Updating copyrights...";

    public UpdateCopyrightProcessor(Project project, PsiFile file)
    {
        super(project, file, TITLE, MESSAGE);
    }

    public UpdateCopyrightProcessor(Project project, PsiFile[] files)
    {
        super(project, files, TITLE, MESSAGE);
    }

    @Override
    protected Runnable preprocessFile(final PsiFile file, final boolean allowReplacement) throws IncorrectOperationException
    {
        VirtualFile vfile = file.getVirtualFile();
        if (vfile == null) return EmptyRunnable.getInstance();
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null) {
            progressIndicator.setText2(vfile.getPresentableUrl());
        }

        return EmptyRunnable.getInstance();

    }
}
