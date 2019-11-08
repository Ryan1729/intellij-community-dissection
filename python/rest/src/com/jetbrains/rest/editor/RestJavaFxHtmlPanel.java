// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.jetbrains.rest.editor;

import com.intellij.ui.javafx.JavaFxHtmlPanel;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

public class RestJavaFxHtmlPanel extends JavaFxHtmlPanel implements RestPreviewPanel {
  private volatile int myYScrollPosition = 0;
  private volatile int myXScrollPosition = 0;

  public RestJavaFxHtmlPanel() {
    super();
    // Dummied out, see README.md
  }

  @Override
  protected URL getStyle(boolean isDarcula) {
    return getClass().getResource(isDarcula ? "/styles/darcula.css" : "/styles/default.css");
  }

  @Override
  public void setHtml(@NotNull String html) {
    super.setHtml(html);
  }
}
