/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.ui.table;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextFieldCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class EditorTextFieldJBTableRowRenderer extends EditorTextFieldCellRenderer implements JBTableRowRenderer {
  protected EditorTextFieldJBTableRowRenderer(@Nullable Project project, @Nullable FileType fileType, @NotNull Disposable parent) {
    super(project, fileType, parent);
  }

  @Override
  public final JComponent getRowRendererComponent(JTable table, int row, boolean selected, boolean focused) {
    return (JComponent)getTableCellRendererComponent(table, null, selected, focused, row, 0);
  }

  @Override
  protected String getText(JTable table, int row) {
    return getText(table, row);
  }

  @Nullable
  @Override
  protected final TextAttributes getTextAttributes(JTable table, int row) {
    return getTextAttributes();
  }

  @Nullable
  protected TextAttributes getTextAttributes() {
    return null;
  }
}
