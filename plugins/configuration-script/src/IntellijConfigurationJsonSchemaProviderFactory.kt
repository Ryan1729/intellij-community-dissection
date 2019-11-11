// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.configurationScript

import com.intellij.configurationScript.providers.PluginsConfiguration
import com.intellij.configurationScript.schemaGenerators.RunConfigurationJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.buildJsonSchema
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.io.JsonObjectBuilder
import org.jetbrains.io.json

internal val LOG = logger<Any>()

internal interface SchemaGenerator {
  fun generate(rootBuilder: JsonObjectBuilder)
}

internal fun doGenerateConfigurationSchema(generators: List<SchemaGenerator>): CharSequence {
  val runConfigurationGenerator = generators.find { it is RunConfigurationJsonSchemaGenerator } as? RunConfigurationJsonSchemaGenerator
  val stringBuilder = StringBuilder()
  stringBuilder.json {
    "\$schema" to "http://json-schema.org/draft-07/schema#"
    "\$id" to "https://jetbrains.com/intellij-configuration.schema.json"
    "title" to "IntelliJ Configuration"
    "description" to "IntelliJ Configuration to configure IDE behavior, run configurations and so on"

    "type" to "object"

    if (runConfigurationGenerator != null) {
      rawMap(RunConfigurationJsonSchemaGenerator.definitionNodeKey) {
        it.append(runConfigurationGenerator.generate())
      }
    }

    map("properties") {
      map(Keys.plugins) {
        "type" to "object"
        "description" to "The plugins"
        map("properties") {
          buildJsonSchema(PluginsConfiguration(), this)
        }
      }

      generators.forEach { it.generate(this) }
    }
    "additionalProperties" to false
  }
  return stringBuilder
}

@Suppress("JsonStandardCompliance")
internal object Keys {
  const val runConfigurations = "runConfigurations"
  const val templates = "templates"

  const val plugins = "plugins"
}