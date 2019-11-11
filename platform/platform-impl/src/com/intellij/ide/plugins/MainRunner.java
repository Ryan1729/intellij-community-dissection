// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.WindowsCommandLineListener;
import com.intellij.idea.Main;
import com.intellij.idea.StartupUtil;

public final class MainRunner  {
  @SuppressWarnings("StaticNonFinalField")
  public static WindowsCommandLineListener LISTENER;
  @SuppressWarnings("StaticNonFinalField")
  public static Activity startupStart;

  /** Called via reflection from {@link Main#bootstrap}. */
  public static void start() {
    startupStart = StartUpMeasurer.startMainActivity("app initialization preparation");

    ThreadGroup threadGroup = new ThreadGroup("Idea Thread Group") {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        StartupAbortedException.processException(e);
      }
    };

    new Thread(threadGroup, () -> {
      try {
        StartupUtil.prepareApp();
      }
      catch (Throwable t) {
        StartupAbortedException.processException(t);
      }
    }, "Idea Main Thread").start();
  }
}