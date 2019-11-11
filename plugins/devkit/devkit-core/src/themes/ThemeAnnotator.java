// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class ThemeAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!(element instanceof JsonProperty)) return;
    if (!ThemeJsonUtil.isThemeFilename(holder.getCurrentAnnotationSession().getFile().getName())) return;

    JsonProperty property = (JsonProperty)element;
    if (property.getValue() instanceof JsonObject) return;  // do not check intermediary keys

    if (!ThemeJsonUtil.isInsideUiProperty(property)) return;

    final Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> pair = ThemeJsonUtil.findMetadata(property);
    if (pair == null) {
      String parentNames = ThemeJsonUtil.getParentNames(property);
      if (parentNames.startsWith("*")) return; // anything allowed

      String fullKey = parentNames.isEmpty() ? property.getName() : parentNames + "." + property.getName();
      holder.createWarningAnnotation(property.getNameElement().getTextRange(),
                                     "Unresolved key '" + fullKey + "'")
        .setHighlightType(ProblemHighlightType.WARNING);
      return;
    }

    if (pair.second.isDeprecated()) {
      holder.createWarningAnnotation(property.getNameElement().getTextRange(),
                                     "Deprecated key '" + pair.second.getKey() + "'")
        .setHighlightType(ProblemHighlightType.LIKE_DEPRECATED);
    }
  }
}
