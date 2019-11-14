// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*

internal class JavaSoftKeywordHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    val visit = file is PsiJavaFile &&
                (file.name == PsiJavaModule.MODULE_INFO_FILE && file.languageLevel.isAtLeast(LanguageLevel.JDK_1_9) ||
                 file.languageLevel.isAtLeast(LanguageLevel.JDK_10))
    return if (visit) JavaSoftKeywordHighlightingPass(file as PsiJavaFile, editor.document) else null
  }
}

private class JavaSoftKeywordHighlightingPass(private val file: PsiJavaFile, document: Document) :
  TextEditorHighlightingPass(file.project, document) {

  private val results = mutableListOf<HighlightInfo>()

  override fun doCollectInformation(progress: ProgressIndicator) {
    file.accept(JavaSoftKeywordHighlightingVisitor(results, file.languageLevel))
  }

  override fun doApplyInformationToEditor() {
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument!!, 0, file.textLength, results, colorsScheme, id)
  }
}

private class JavaSoftKeywordHighlightingVisitor(private val results: MutableList<HighlightInfo>, private val level: LanguageLevel) :
  JavaRecursiveElementVisitor() {

  override fun visitKeyword(keyword: PsiKeyword) {
  }
}