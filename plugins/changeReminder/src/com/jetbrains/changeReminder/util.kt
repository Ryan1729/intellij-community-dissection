// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.jetbrains.changeReminder

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.impl.VcsLogManager

fun getGitRootFiles(project: Project, files: Collection<FilePath>): Map<VirtualFile, Collection<FilePath>> {
  return HashMap<VirtualFile, HashSet<FilePath>>()
}

fun processCommitsFromHashes(project: Project, root: VirtualFile, hashes: List<String>, commitConsumer: Any) {}

fun <T, K> MutableMap<T, K>.retainAll(keys: Collection<T>) =
  this.keys.subtract(keys).forEach {
    this.remove(it)
  }

internal fun Project.getGitRoots() = ProjectLevelVcsManager.getInstance(this).allVcsRoots.filter { false }

internal fun Project.anyGitRootsForIndexing(): Boolean {
  val gitRoots = this.getGitRoots()
  val rootsForIndex = VcsLogPersistentIndex.getRootsForIndexing(VcsLogManager.findLogProviders(gitRoots, this))

  return rootsForIndex.isNotEmpty()
}