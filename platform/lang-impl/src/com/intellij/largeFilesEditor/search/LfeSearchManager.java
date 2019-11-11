// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.largeFilesEditor.search;

import com.intellij.find.SearchReplaceComponent;
import com.intellij.largeFilesEditor.editor.LargeFileEditor;
import com.intellij.largeFilesEditor.search.searchTask.CloseSearchTask;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface LfeSearchManager {

  void updateSearchReplaceComponentActions();

  SearchReplaceComponent getSearchReplaceComponent();

  CloseSearchTask getLastExecutedCloseSearchTask();

  void onSearchActionHandlerExecuted();

  @NotNull
  LargeFileEditor getLargeFileEditor();

  void launchNewRangeSearch(long fromPageNumber, long toPageNumber, boolean forwardDirection);

  void gotoNextOccurrence(boolean directionForward);

  void onEscapePressed();

  String getStatusText();

  void updateStatusText();

  void onSearchParametersChanged();

  void onCaretPositionChanged(CaretEvent e);

  void dispose();

  List<TextRange> getAllSearchResultsInDocument(Document document);

  boolean isSearchWorkingNow();
}
