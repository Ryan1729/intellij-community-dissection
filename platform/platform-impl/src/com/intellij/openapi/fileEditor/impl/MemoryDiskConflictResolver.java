// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
class MemoryDiskConflictResolver {
  private static final Logger LOG = Logger.getInstance(MemoryDiskConflictResolver.class);

  private final Set<VirtualFile> myConflicts = new LinkedHashSet<>();
  private Throwable myConflictAppeared;

  void beforeContentChange(@NotNull VFileContentChangeEvent event) {
    if (event.isFromSave()) return;

    VirtualFile file = event.getFile();
    if (!file.isValid() || hasConflict(file)) return;

    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document == null || !FileDocumentManager.getInstance().isDocumentUnsaved(document)) return;

    long documentStamp = document.getModificationStamp();
    long oldFileStamp = event.getOldModificationStamp();
    if (documentStamp != oldFileStamp) {
      LOG.info("reload " + file.getName() + " from disk?");
      LOG.info("  documentStamp:" + documentStamp);
      LOG.info("  oldFileStamp:" + oldFileStamp);
      if (myConflicts.isEmpty()) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          myConflictAppeared = new Throwable();
        }
        ApplicationManager.getApplication().invokeLater(this::processConflicts);
      }
      myConflicts.add(file);
    }
  }

  boolean hasConflict(VirtualFile file) {
    return myConflicts.contains(file);
  }

  private void processConflicts() {
    List<VirtualFile> conflicts = new ArrayList<>(myConflicts);
    myConflicts.clear();

    for (VirtualFile file : conflicts) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null && file.getModificationStamp() != document.getModificationStamp() && askReloadFromDisk(file, document)) {
        FileDocumentManager.getInstance().reloadFromDisk(document);
      }
    }
    myConflictAppeared = null;
  }

  boolean askReloadFromDisk(VirtualFile file, Document document) {
    if (myConflictAppeared != null) {
      Throwable trace = myConflictAppeared;
      myConflictAppeared = null;
      throw new IllegalStateException("Unexpected memory-disk conflict in tests for " + file.getPath() +
                                      ", please use FileDocumentManager#reloadFromDisk or avoid VFS refresh", trace);
    }

    String message = UIBundle.message("file.cache.conflict.message.text", file.getPresentableUrl());

    DialogBuilder builder = new DialogBuilder();
    builder.setCenterPanel(new JLabel(message, Messages.getQuestionIcon(), SwingConstants.CENTER));
    builder.addOkAction().setText(UIBundle.message("file.cache.conflict.load.fs.changes.button"));
    builder.addCancelAction().setText(UIBundle.message("file.cache.conflict.keep.memory.changes.button"));
    builder.setTitle(UIBundle.message("file.cache.conflict.dialog.title"));
    builder.setButtonsAlignment(SwingConstants.CENTER);
    builder.setHelpId("reference.dialogs.fileCacheConflict");
    return builder.show() == 0;
  }
}