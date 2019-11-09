//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.jps.cache.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;

public class GitRepositoryUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.git.GitRepositoryUtil");

  private GitRepositoryUtil() {}

  public static Iterator<String> getCommitsIterator(@NotNull Project project) {
    return Collections.emptyIterator();
  }
}
