// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class YAMLKeysSearchEverywhereContributor implements SearchEverywhereContributor<YAMLKeyNavigationItem> {
  final Project myProject;

  public YAMLKeysSearchEverywhereContributor(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getSearchProviderId() {
    return getClass().getSimpleName();
  }

  @NotNull
  @Override
  public String getGroupName() {
    return YAMLBundle.message("YAMLKeysSearchEverywhereContributor.group.name");
  }

  @Override
  public int getSortWeight() {
    // lets show yaml keys in the last order
    // all other implementations have weight from 50 to 300
    return 1000;
  }

  @Override
  public boolean showInFindResults() {
    return true;
  }

  @Override
  public void fetchElements(@NotNull String pattern,
                            @NotNull ProgressIndicator progressIndicator,
                            @NotNull Processor<? super YAMLKeyNavigationItem> consumer) {
    if (myProject == null || pattern.isEmpty()) {
      return;
    }

    Runnable task = () -> findKeys(consumer, pattern, progressIndicator);
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      application.runReadAction(task);
    } else {
      ProgressIndicatorUtils.yieldToPendingWriteActions();
      ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(task, progressIndicator);
    }
  }

  @Override
  public boolean processSelectedItem(@NotNull YAMLKeyNavigationItem selected, int modifiers, @NotNull String searchText) {
    ((Navigatable)selected).navigate(true);
    return true;
  }

  @NotNull
  @Override
  public ListCellRenderer<? super Object> getElementsRenderer() {
    return new NavigationItemListCellRenderer();
  }

  @Override
  public Object getDataForItem(@NotNull YAMLKeyNavigationItem element, @NotNull String dataId) {
    return null;
  }

  private void findKeys(@NotNull Processor<? super YAMLKeyNavigationItem> consumer,
                        @NotNull String pattern,
                        ProgressIndicator progressIndicator) {
    if (ActionUtil.isDumbMode(myProject)) {
      return;
    }
    assert myProject != null;

    Collection<String> allKeys = FileBasedIndex.getInstance().getAllKeys(YAMLKeysIndex.KEY, myProject);

    List<String> sorted = applyPattern(allKeys, pattern, progressIndicator);

    boolean everywhere = SearchEverywhereManager.getInstance(myProject).isEverywhere();
    for (String name : sorted) {
      progressIndicator.checkCanceled();
      CommonProcessors.CollectProcessor<VirtualFile> files = new CommonProcessors.CollectProcessor<>();
      GlobalSearchScope filter = everywhere ? ProjectScope.getAllScope(myProject) : ProjectScope.getProjectScope(myProject);
      FileBasedIndex.getInstance().getFilesWithKey(YAMLKeysIndex.KEY,
                                                   Collections.singleton(name),
                                                   files,
                                                   filter);

      for (VirtualFile file : files.getResults()) {
        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
        if (psiFile == null) {
          continue;
        }

        List<Integer> positions =
          FileBasedIndex.getInstance().getValues(YAMLKeysIndex.KEY, name, GlobalSearchScope.fileScope(myProject, file));
        if (positions.size() > 1) {
          // should be seldom case
          positions = new ArrayList<>(positions);
          Collections.sort(positions);
        }
        for (int pos : positions) {
          Navigatable navigatable = PsiNavigationSupport.getInstance().createNavigatable(myProject, file, pos);
          if (!consumer.process(new YAMLKeyNavigationItem(navigatable, name, file))) {
            return;
          }
        }
      }
    }
  }

  @Contract(pure = true)
  @NotNull
  private static List<String> applyPattern(@NotNull Collection<String> keys,
                                           @NotNull String pattern,
                                           ProgressIndicator progressIndicator) {
    MultiMap<Integer, String> priority = MultiMap.create();

    for (String key : keys) {
      progressIndicator.checkCanceled();
      int start = key.indexOf(pattern);
      if (start == -1) {
        continue;
      }
      if (start > 0 && key.charAt(start - 1) != '.') {
        continue;
      }
      if (start + pattern.length() < key.length() &&
          key.indexOf(".", start + pattern.length()) != -1) {
        continue;
      }
      int dots = countDots(key, start);
      priority.putValue(dots, key);
    }

    progressIndicator.checkCanceled();
    return priority
      .keySet()
      .stream()
      .sorted()
      .map(idx -> priority.get(idx))
      .map(found -> found.stream().sorted())
      .flatMap(s -> s)
      .collect(Collectors.toList());
  }


  @Contract(pure = true)
  private static int countDots(@NotNull String path, int end) {
    int count = 0;
    for (int i = 0; i < end; i++) {
      if (path.charAt(i) == '.') {
        count++;
      }
    }
    return count;
  }

  public static class Factory implements SearchEverywhereContributorFactory<YAMLKeyNavigationItem> {
    @NotNull
    @Override
    public SearchEverywhereContributor<YAMLKeyNavigationItem> createContributor(@NotNull AnActionEvent initEvent) {
      return new YAMLKeysSearchEverywhereContributor(initEvent.getProject());
    }
  }
}
