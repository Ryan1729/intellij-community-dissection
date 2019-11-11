// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import javax.swing.JFrame

internal class LinuxIdeMenuBar : IdeMenuBar() {
  companion object {
    @JvmStatic
    fun doBindAppMenuOfParent(frame: Window, parent: IdeFrame?) {
      if (!GlobalMenuLinux.isAvailable() || parent !is JFrame) {
        return
      }

      if (frame is JFrame && GlobalMenuLinux.isPresented()) {
        if (frame.jMenuBar != null) {
          // all children of IdeFrame mustn't show swing-menubar
          frame.jMenuBar.isVisible = false
        }
      }

      val menuBar = (parent as JFrame).jMenuBar
      val globalMenu = (menuBar as? LinuxIdeMenuBar)?.globalMenu ?: return
      frame.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent?) {
          globalMenu.unbindWindow(frame)
        }

        override fun windowOpened(e: WindowEvent?) {
          globalMenu.bindNewWindow(frame)
        }
      })
    }
  }

  private var globalMenu: GlobalMenuLinux? = null

  override fun isDarkMenu(): Boolean {
    return super.isDarkMenu() || globalMenu != null
  }

  override fun updateGlobalMenuRoots() {
    if (globalMenu != null) {
      val roots: MutableList<ActionMenu?> = ArrayList()
      for (each in components) {
        if (each is ActionMenu) {
          roots.add(each)
        }
      }
      globalMenu!!.setRoots(roots)
    }
  }
}