/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

class SliceLeafValueClassNode extends SliceLeafValueRootNode {
  private final String myClassName;

  SliceLeafValueClassNode(@NotNull Project project, @NotNull SliceNode root, @NotNull String className) {
    super(project,
          root,
          JavaSliceUsage.createRootUsage(root.getValue().getElement(), root.getValue().params),
          new ArrayList<>());
    myClassName = className;
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void customizeCellRenderer(@NotNull SliceUsageCellRendererBase renderer,
                                    @NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    renderer.append(myClassName, SimpleTextAttributes.DARK_TEXT);
  }

  @Override
  public String getNodeText() {
    return myClassName;
  }
}