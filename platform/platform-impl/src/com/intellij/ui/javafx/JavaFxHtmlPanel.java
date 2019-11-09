// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.ui.javafx;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public class JavaFxHtmlPanel implements Disposable {
  public JavaFxHtmlPanel() {
    // Dummied out, see README.md
  }

  public void setBackground(Color background) {

  }

  @NotNull
  public JComponent getComponent() {
    return null;
  }

  public void setHtml(@NotNull String html) {
  }

  public void render() {

  }

  /**
   * @return user style, used to display HTML
   * @see WebEngine#setUserStyleSheetLocation(String)
   * @see #getJavaFxStyle(boolean)
   */
  @Nullable
  protected URL getStyle(boolean isDarcula) {
    return null;
  }

  @Override
  public void dispose() {

  }
}
