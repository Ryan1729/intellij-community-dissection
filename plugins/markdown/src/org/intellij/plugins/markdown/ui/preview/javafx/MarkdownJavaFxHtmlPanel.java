// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package org.intellij.plugins.markdown.ui.preview.javafx;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.javafx.JavaFxHtmlPanel;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownJavaFxHtmlPanel extends JavaFxHtmlPanel implements MarkdownHtmlPanel {
  @NotNull
  private String myLastRawHtml = "";

  public MarkdownJavaFxHtmlPanel() {
    super();
    subscribeForGrayscaleSetting();
    // Dummied out, see README.md
  }

  private void subscribeForGrayscaleSetting() {
  }

  @Override
  public void setHtml(@NotNull String html) {
    myLastRawHtml = html;
    super.setHtml(html);
  }

  @Override
  public void setCSS(@Nullable String inlineCss, @NotNull String... fileUris) {
    PreviewStaticServer.getInstance().setInlineStyle(inlineCss);
    setHtml(myLastRawHtml);
  }

  @Override
  public void scrollToMarkdownSrcOffset(final int offset) {
  }

  @Override
  public void dispose() {
  }

  @SuppressWarnings("unused")
  public static class JavaPanelBridge {
    static final JavaPanelBridge INSTANCE = new JavaPanelBridge();
    private static final NotificationGroup MARKDOWN_NOTIFICATION_GROUP =
      NotificationGroup.toolWindowGroup(MarkdownBundle.message("markdown.navigate.to.header.group"), ToolWindowId.MESSAGES_WINDOW);

    public void openInExternalBrowser(@NotNull String link) {
      SafeOpener.openLink(link);
    }

    public void log(@Nullable String text) {
      Logger.getInstance(JavaPanelBridge.class).warn(text);
    }
  }
}
