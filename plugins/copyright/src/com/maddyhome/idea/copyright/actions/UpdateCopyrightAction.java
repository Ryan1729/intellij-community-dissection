// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.

package com.maddyhome.idea.copyright.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.*;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateCopyrightAction extends BaseAnalysisAction {
  public static final String UPDATE_EXISTING_COPYRIGHTS = "update.existing.copyrights";
  private JCheckBox myUpdateExistingCopyrightsCb;

  protected UpdateCopyrightAction() {
    super(UpdateCopyrightProcessor.TITLE, UpdateCopyrightProcessor.TITLE);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final boolean enabled = isEnabled();
    event.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      event.getPresentation().setVisible(enabled);
    }
  }

  private static boolean isEnabled() {
    return false;
  }

  @Nullable
  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.add(new TitledSeparator());
    myUpdateExistingCopyrightsCb = new JCheckBox("Update existing copyrights",
                                                 PropertiesComponent.getInstance().getBoolean(UPDATE_EXISTING_COPYRIGHTS, true));
    panel.add(myUpdateExistingCopyrightsCb);
    return panel;
  }

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    PropertiesComponent.getInstance().setValue(UPDATE_EXISTING_COPYRIGHTS, String.valueOf(myUpdateExistingCopyrightsCb.isSelected()), "true");
    final Map<PsiFile, Runnable> preparations = new LinkedHashMap<>();
    Task.Backgroundable task = new Task.Backgroundable(project, "Prepare Copyright...", true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        scope.accept(new PsiElementVisitor() {
          @Override
          public void visitFile(final PsiFile file) {
            if (indicator.isCanceled()) {
              return;
            }
            final UpdateCopyrightProcessor processor = new UpdateCopyrightProcessor(project, file);
            final Runnable runnable = processor.preprocessFile(file, myUpdateExistingCopyrightsCb.isSelected());
            if (runnable != EmptyRunnable.getInstance()) {
              preparations.put(file, runnable);
            }
          }
        });
      }

      @Override
      public void onSuccess() {
        if (!preparations.isEmpty()) {
          if (!FileModificationService.getInstance().preparePsiElementsForWrite(preparations.keySet())) return;
          final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, UpdateCopyrightProcessor.TITLE, true);
          progressTask.setMinIterationTime(200);
          progressTask.setTask(new UpdateCopyrightSequentialTask(preparations, progressTask));
          CommandProcessor.getInstance().executeCommand(project, () -> {
            CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
            ProgressManager.getInstance().run(progressTask);
          }, getTemplatePresentation().getText(), null);
        }
      }
    };

    ProgressManager.getInstance().run(task);
  }

  private static class UpdateCopyrightSequentialTask implements SequentialTask {
    private final int mySize;
    private final Iterator<Runnable> myRunnables;
    private final SequentialModalProgressTask myProgressTask;
    private int myIdx = 0;

    private UpdateCopyrightSequentialTask(Map<PsiFile, Runnable> runnables, SequentialModalProgressTask progressTask) {
      myRunnables = runnables.values().iterator();
      myProgressTask = progressTask;
      mySize = runnables.size();
    }

    @Override
    public boolean isDone() {
      return myIdx > mySize - 1;
    }

    @Override
    public boolean iteration() {
      final ProgressIndicator indicator = myProgressTask.getIndicator();
      if (indicator != null) {
        indicator.setFraction((double) myIdx/mySize);
      }
      myRunnables.next().run();
      myIdx++;
      return true;
    }

    @Override
    public void stop() {
      myIdx = mySize;
    }
  }
}