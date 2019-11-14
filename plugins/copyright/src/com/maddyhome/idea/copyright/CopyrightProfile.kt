// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.maddyhome.idea.copyright

import com.intellij.configurationStore.SerializableScheme
import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import org.jdom.Element

class CopyrightProfile @JvmOverloads constructor(profileName: String? = null) : ExternalizableScheme, BaseState(), SerializableScheme {
  // ugly name to preserve compatibility
  // must be not private because otherwise binding is not created for private accessor
  @Suppress("MemberVisibilityCanBePrivate")
  @get:OptionTag("myName")
  var profileName: String? by string()

  var notice: String? by string("")
  var keyword: String? by string("Copyright")
  var allowReplaceRegexp: String? by string()

  @Deprecated("use allowReplaceRegexp instead", ReplaceWith(""))
  var allowReplaceKeyword: String? by string()

  init {
    // otherwise will be as default value and name will be not serialized
    this.profileName = profileName
  }

  // ugly name to preserve compatibility
  @Transient
  override fun getName(): String = profileName ?: ""

  override fun setName(value: String) {
    profileName = value
  }

  override fun toString(): String = profileName ?: ""

  override fun writeScheme(): Element {
    val element = Element("copyright")
    serializeObjectInto(this, element)
    return element
  }
}
