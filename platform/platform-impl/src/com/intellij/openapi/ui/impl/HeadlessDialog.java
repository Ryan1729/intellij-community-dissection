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
package com.intellij.openapi.ui.impl;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.IdeFocusManagerHeadless;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
class HeadlessDialog implements AbstractDialog {
  @NotNull private final DialogWrapper myWrapper;
  private String myTitle;

  HeadlessDialog(@NotNull DialogWrapper wrapper) {
    myWrapper = wrapper;
  }

  @Override
  public void setUndecorated() {
  }

  @Override
  public void addMouseListener() {
  }

  @Override
  public void addMouseMotionListener() {
  }

  @Override
  public void addKeyListener() {
  }

  @Override
  public void setModal(boolean b) {
  }

  @Override
  public void toFront() {
  }

  @Override
  public void setContentPane() {
  }

  @Override
  public void centerInParent() {
  }

  @Override
  public void toBack() {
  }

  @Override
  public JRootPane getRootPane() {
    return null;
  }

  @Override
  public void remove() {
  }

  @Override
  public Container getContentPane() {
    return null;
  }

  @Override
  public void validate() {
  }

  @Override
  public void repaint() {
  }

  @Override
  public Window getOwner() {
    return null;
  }

  @Override
  public JDialog getWindow() {
    return null;
  }

  @Override
  public Dimension getSize() {
    return null;
  }

  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public void pack() {
  }

  @Override
  public Dimension getPreferredSize() {
    return null;
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public boolean isShowing() {
    return false;
  }

  @Override
  public void setSize(int width, int height) {
  }

  @Override
  public void setTitle(String title) {
    myTitle = title;
  }

  @Override
  public boolean isResizable() {
    return false;
  }

  @Override
  public void setResizable() {
  }

  @NotNull
  @Override
  public Point getLocation() {
    return new Point(0,0);
  }

  @Override
  public void setLocation() {
  }

  @Override
  public boolean isModal() {
    return false;
  }

  @Override
  public void setModalityType() {
  }

  @Override
  public Dialog.ModalityType getModalityType() {
    return null;
  }

  @Override
  public void show() {
    myWrapper.close(DialogWrapper.OK_EXIT_CODE);
  }

  @NotNull
  @Override
  public IdeFocusManager getFocusManager() {
    return new IdeFocusManagerHeadless();
  }

  @Override
  public void dispose() {
  }
}
