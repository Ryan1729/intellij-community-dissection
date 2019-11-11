/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.openapi.ui.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
interface AbstractDialog extends Disposable {
  void setUndecorated();

  void addMouseListener();

  void addMouseMotionListener();

  void addKeyListener();

  /**
   * @deprecated use {@link #setModalityType()}
   */
  @Deprecated
  void setModal(boolean b);

  void toFront();

  void setContentPane();

  void centerInParent();

  void toBack();

  JRootPane getRootPane();

  void remove();

  Container getContentPane();

  void validate();

  void repaint();

  Window getOwner();

  JDialog getWindow();

  Dimension getSize();

  String getTitle();

  void pack();

  Dimension getPreferredSize();

  boolean isVisible();

  boolean isShowing();

  void setSize(int width, int height);

  void setTitle(String title);

  boolean isResizable();

  void setResizable();

  @NotNull
  Point getLocation();

  void setLocation();

  /**
   * @deprecated use {@link #getModalityType()}
   */
  @Deprecated
  boolean isModal();

  void setModalityType();

  Dialog.ModalityType getModalityType();

  void show();

  @NotNull
  IdeFocusManager getFocusManager();
}
