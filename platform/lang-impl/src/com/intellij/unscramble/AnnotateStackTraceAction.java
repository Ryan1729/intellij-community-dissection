// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.unscramble;

import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

public class AnnotateStackTraceAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(AnnotateStackTraceAction.class);

  private final EditorHyperlinkSupport myHyperlinks;
  private final Editor myEditor;

  private boolean myIsLoading = false;

  public AnnotateStackTraceAction(@NotNull Editor editor, @NotNull EditorHyperlinkSupport hyperlinks) {
    super("Show files modification info", null, AllIcons.Actions.Annotate);
    myHyperlinks = hyperlinks;
    myEditor = editor;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isShown = myEditor.getGutter().isAnnotationsShown();
    e.getPresentation().setEnabled(!isShown && !myIsLoading);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
  }

  @Nullable
  private static VirtualFile getHyperlinkVirtualFile(@NotNull List<RangeHighlighter> links) {
    RangeHighlighter key = ContainerUtil.getLastItem(links);
    if (key == null) return null;
    HyperlinkInfo info = EditorHyperlinkSupport.getHyperlinkInfo(key);
    if (!(info instanceof FileHyperlinkInfo)) return null;
    OpenFileDescriptor descriptor = ((FileHyperlinkInfo)info).getDescriptor();
    return descriptor != null ? descriptor.getFile() : null;
  }
}
