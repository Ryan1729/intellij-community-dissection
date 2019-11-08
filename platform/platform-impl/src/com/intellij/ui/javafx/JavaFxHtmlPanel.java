// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.javafx;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class JavaFxHtmlPanel implements Disposable {
  // flag is reset after check
  public static final String JAVAFX_INITIALIZATION_INCOMPLETE_PROPERTY = "js.debugger.javafx.inititalization";
  @NotNull
  private final List<Runnable> myInitActions = new ArrayList<>();
  private Color background;

  public JavaFxHtmlPanel() {
    // Dummied out, see README.md
  }

  public void setBackground(Color background) {

  }


  private static void runFX(@NotNull Runnable r) {
    IdeEventQueue.unsafeNonblockingExecute(r);
  }

  protected void runInPlatformWhenAvailable(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  @NotNull
  public JComponent getComponent() {
    return null;
  }

  public void setHtml(@NotNull String html) {
  }

  @NotNull
  protected String prepareHtml(@NotNull String html) {
    return html;
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

  /**
   * @return java fx style, used for menus etc.
   * See <a href="https://docs.oracle.com/javafx/2/api/javafx/scene/doc-files/cssref.html">manual</a>
   * @see Scene#getStylesheets()
   * @see #getStyle(boolean)
   */
  @Nullable
  protected URL getJavaFxStyle(boolean isDarcula) {
    return null;
  }


  private class JavaFXLafManagerListener implements LafManagerListener {
    @Override
    public void lookAndFeelChanged(@NotNull LafManager manager) {
      updateLaf(manager.getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo);
    }
  }

  private void updateLaf(boolean isDarcula) {
  }

  @Override
  public void dispose() {

  }
}
