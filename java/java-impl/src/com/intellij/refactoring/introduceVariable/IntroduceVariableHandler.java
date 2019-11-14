/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class IntroduceVariableHandler extends IntroduceVariableBase {

  public void invoke(@NotNull final Project project, final Editor editor, final PsiExpression expression) {
    invokeImpl(project, expression, editor);
  }

  @Override
  public Object getSettings(Project project, Editor editor,
                            PsiExpression expr, PsiExpression[] occurrences,
                            boolean declareFinalIfAll,
                            boolean anyAssignmentLHS,
                            PsiElement anchor, JavaReplaceChoice replaceChoice) {
    if (replaceChoice == null && ApplicationManager.getApplication().isUnitTestMode()) {
      replaceChoice = JavaReplaceChoice.NO;
    }
    if (replaceChoice != null) {
      return super.getSettings(project, editor, expr, occurrences, declareFinalIfAll, anyAssignmentLHS,
                               anchor, replaceChoice);
    }
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    HighlightManager highlightManager = null;
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (occurrences.length > 1) {
        highlightManager.addOccurrenceHighlights(editor, occurrences, attributes, true, highlighters);
      }
    }

    {
      if (editor != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return null;
  }

  @Override
  protected void showErrorMessage(final Project project, Editor editor, String message) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, "HelpID.INTRODUCE_VARIABLE");
  }

  @Override
  protected boolean acceptLocalVariable() {
    return false;
  }
}
