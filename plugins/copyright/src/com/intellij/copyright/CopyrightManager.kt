// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.copyright

import com.intellij.configurationStore.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.project.isDirectoryBased
import com.intellij.util.containers.ContainerUtil
import com.maddyhome.idea.copyright.CopyrightProfile
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

private const val DEFAULT = "default"
private const val MODULE_TO_COPYRIGHT = "module2copyright"
private const val COPYRIGHT = "copyright"
private const val ELEMENT = "element"
private const val MODULE = "module"

private val LOG = Logger.getInstance(CopyrightManager::class.java)

@State(name = "CopyrightManager", storages = [(Storage(value = "copyright/profiles_settings.xml", exclusive = true))])
class CopyrightManager @JvmOverloads constructor(private val project: Project, schemeManagerFactory: SchemeManagerFactory = SchemeManagerFactory.getInstance(project), isSupportIprProjects: Boolean = true) : PersistentStateComponent<Element> {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<CopyrightManager>()
  }

  private var defaultCopyrightName: String? = null

  var defaultCopyright: CopyrightProfile?
    get() = defaultCopyrightName?.let { schemeManager.findSchemeByName(it)?.scheme }
    set(value) {
      defaultCopyrightName = value?.name
    }

  val scopeToCopyright = LinkedHashMap<String, String>()

  private val schemeWriter = { scheme: CopyrightProfile ->
    val element = scheme.writeScheme()
    if (project.isDirectoryBased) wrapScheme(element) else element
  }

  private val schemeManagerIprProvider = if (project.isDirectoryBased || !isSupportIprProjects) null else SchemeManagerIprProvider("copyright")

  private val schemeManager = schemeManagerFactory.create("copyright", object : LazySchemeProcessor<SchemeWrapper<CopyrightProfile>, SchemeWrapper<CopyrightProfile>>("myName") {
    override fun createScheme(dataHolder: SchemeDataHolder<SchemeWrapper<CopyrightProfile>>,
                              name: String,
                              attributeProvider: Function<in String, String?>,
                              isBundled: Boolean): SchemeWrapper<CopyrightProfile> {
      return CopyrightLazySchemeWrapper(name, dataHolder, schemeWriter)
    }

    override fun isSchemeFile(name: CharSequence) = !StringUtil.equals(name, "profiles_settings.xml")

    override fun getSchemeKey(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String): String {
      val schemeKey = super.getSchemeKey(attributeProvider, fileNameWithoutExtension)
      if (schemeKey != null) {
        return schemeKey
      }
      LOG.warn("Name is not specified for scheme $fileNameWithoutExtension, file name will be used instead")
      return fileNameWithoutExtension
    }
  }, schemeNameToFileName = OLD_NAME_CONVERTER, streamProvider = schemeManagerIprProvider)

  init {
    val app = ApplicationManager.getApplication()
    if (project.isDirectoryBased || !app.isUnitTestMode) {
      schemeManager.loadSchemes()
    }
  }

  @TestOnly
  fun loadSchemes() {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode)
    schemeManager.loadSchemes()
  }

  fun mapCopyright(scopeName: String, copyrightProfileName: String) {
    scopeToCopyright.put(scopeName, copyrightProfileName)
  }

  fun unmapCopyright(scopeName: String) {
    scopeToCopyright.remove(scopeName)
  }

  fun hasAnyCopyrights(): Boolean {
    return defaultCopyrightName != null || !scopeToCopyright.isEmpty()
  }

  override fun getState(): Element? {
    val result = Element("settings")
    try {
      schemeManagerIprProvider?.writeState(result)

      if (!scopeToCopyright.isEmpty()) {
        val map = Element(MODULE_TO_COPYRIGHT)
        for ((scopeName, profileName) in scopeToCopyright) {
          val e = Element(ELEMENT)
          e
            .setAttribute(MODULE, scopeName)
            .setAttribute(COPYRIGHT, profileName)
          map.addContent(e)
        }
        result.addContent(map)
      }

    }
    catch (e: WriteExternalException) {
      LOG.error(e)
      return null
    }

    defaultCopyrightName?.let {
      result.setAttribute(DEFAULT, it)
    }

    return wrapState(result, project)
  }

  override fun loadState(state: Element) {
    val data = unwrapState(state, project, schemeManagerIprProvider, schemeManager) ?: return
    data.getChild(MODULE_TO_COPYRIGHT)?.let {
      for (element in it.getChildren(ELEMENT)) {
        scopeToCopyright.put(element.getAttributeValue(MODULE), element.getAttributeValue(COPYRIGHT))
      }
    }

    try {
      defaultCopyrightName = data.getAttributeValue(DEFAULT)
    }
    catch (e: InvalidDataException) {
      LOG.error(e)
    }
  }

  private fun addCopyright(profile: CopyrightProfile) {
    schemeManager.addScheme(InitializedSchemeWrapper(profile, schemeWriter))
  }

  fun getCopyrightOptions(): CopyrightProfile? {
    return null
  }
}

private class CopyrightManagerDocumentListener : BulkFileListener {
  private val newFilePaths = ContainerUtil.newConcurrentSet<String>()

  private val isDocumentListenerAdded = AtomicBoolean()

  override fun after(events: List<VFileEvent>) {
    for (event in events) {
      if (event.isFromRefresh) {
        continue
      }

      if (event is VFileCreateEvent || event is VFileMoveEvent) {
        newFilePaths.add(event.path)
        if (isDocumentListenerAdded.compareAndSet(false, true)) {
          addDocumentListener()
        }
      }
    }
  }

  private fun addDocumentListener() {
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        if (newFilePaths.isEmpty()) {
          return
        }

        val virtualFile = FileDocumentManager.getInstance().getFile(e.document) ?: return
        if (!newFilePaths.remove(virtualFile.path)) {
          return
        }

        val projectManager = serviceIfCreated<ProjectManager>() ?: return
        for (project in projectManager.openProjects) {
          if (project.isDisposed) {
            continue
          }

          handleEvent()
        }
      }
    }, ApplicationManager.getApplication())
  }

  private fun handleEvent() {
    return
  }
}

private fun wrapScheme(element: Element): Element {
  val wrapper = Element("component")
      .setAttribute("name", "CopyrightManager")
  wrapper.addContent(element)
  return wrapper
}

private class CopyrightLazySchemeWrapper(name: String,
                                         dataHolder: SchemeDataHolder<SchemeWrapper<CopyrightProfile>>,
                                         writer: (scheme: CopyrightProfile) -> Element,
                                         private val subStateTagName: String = "copyright") : LazySchemeWrapper<CopyrightProfile>(name, dataHolder, writer) {
  override val lazyScheme = lazy {
    val scheme = CopyrightProfile()
    @Suppress("NAME_SHADOWING")
    val dataHolder = this.dataHolder.getAndSet(null)
    var element = dataHolder.read()
    if (element.name != subStateTagName) {
      element = element.getChild(subStateTagName)
    }

    element.deserializeInto(scheme)
    // use effective name instead of probably missed from the serialized
    // https://youtrack.jetbrains.com/v2/issue/IDEA-186546
    scheme.profileName = name

    @Suppress("DEPRECATION")
    val allowReplaceKeyword = scheme.allowReplaceKeyword
    if (allowReplaceKeyword != null && scheme.allowReplaceRegexp == null) {
      scheme.allowReplaceRegexp = StringUtil.escapeToRegexp(allowReplaceKeyword)
      @Suppress("DEPRECATION")
      scheme.allowReplaceKeyword = null
    }

    scheme.resetModificationCount()
    dataHolder.updateDigest(writer(scheme))
    scheme
  }
}