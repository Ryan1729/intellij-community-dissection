// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.ide.browsers

import com.intellij.concurrency.JobScheduler
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkNoDialog
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AppUIUtil
import com.intellij.util.Urls
import java.util.concurrent.TimeUnit

class BrowserLauncherImpl : BrowserLauncherAppless() {
  override fun getEffectiveBrowser(browser: WebBrowser?): WebBrowser? {
    var effectiveBrowser = browser
    if (browser == null) {
      // https://youtrack.jetbrains.com/issue/WEB-26547
      val browserManager = WebBrowserManager.getInstance()
      if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST) {
        effectiveBrowser = browserManager.firstActiveBrowser
      }
    }
    return effectiveBrowser
  }

  override fun signUrl(url: String): String {
    @Suppress("NAME_SHADOWING")
    var url = url
    @Suppress("NAME_SHADOWING")
    val parsedUrl = Urls.parse(url, false)
    if (parsedUrl != null) {
      if (Registry.`is`("ide.built.in.web.server.activatable", false)) {
        PropertiesComponent.getInstance().setValue("ide.built.in.web.server.active", true)
      }
    }
    return url
  }

  override fun openWithExplicitBrowser(url: String, settings: GeneralSettings, project: Project?) {
    val browserManager = WebBrowserManager.getInstance()
    if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST) {
      browserManager.firstActiveBrowser?.let {
        browse(url, it, project)
        return
      }
    }
    else if (SystemInfo.isMac && "open" == settings.browserPath) {
      browserManager.firstActiveBrowser?.let {
        browseUsingPath(url, null, it, project)
        return
      }
    }

    super.openWithExplicitBrowser(url, settings, project)
  }

  override fun showError(error: String?, browser: WebBrowser?, project: Project?, title: String?, launchTask: (() -> Unit)?) {
    AppUIUtil.invokeOnEdt(Runnable {
      if (showOkNoDialog(title ?: IdeBundle.message("browser.error"), error ?: "Unknown error", project,
                         okText = IdeBundle.message("button.fix"),
                         noText = Messages.OK_BUTTON)) {
        val browserSettings = BrowserSettings()
        if (ShowSettingsUtil.getInstance().editConfigurable(project, browserSettings, browser?.let { Runnable { browserSettings.selectBrowser(it) } })) {
          launchTask?.invoke()
        }
      }
    }, project?.disposed)
  }

  override fun checkCreatedProcess(browser: WebBrowser?, project: Project?, commandLine: GeneralCommandLine, process: Process, launchTask: (() -> Unit)?) {
    if (isOpenCommandUsed(commandLine)) {
      val future = ApplicationManager.getApplication().executeOnPooledThread {
        try {
          if (process.waitFor() == 1) {
            showError(ExecUtil.readFirstLine(process.errorStream, null), browser, project, null, launchTask)
          }
        }
        catch (ignored: InterruptedException) {
        }
      }
      // 10 seconds is enough to start
      JobScheduler.getScheduler().schedule({ future.cancel(true) }, 10, TimeUnit.SECONDS)
    }
  }
}