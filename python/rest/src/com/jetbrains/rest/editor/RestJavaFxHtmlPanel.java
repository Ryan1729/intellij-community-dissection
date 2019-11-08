// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.editor;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.javafx.JavaFxHtmlPanel;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import org.w3c.dom.html.HTMLAnchorElement;

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
