// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.analysis

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.analysis.UastAnalysisPlugin

class JavaUastAnalysisPlugin : UastAnalysisPlugin {
  override fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T? {
    when (fact) {
      is UExpressionFact.UNullabilityFact -> {
        val psiExpression = sourcePsi as? PsiExpression ?: return null
        val nullability = UNullability.UNKNOWN

        @Suppress("UNCHECKED_CAST")
        return nullability as T
      }
    }
  }

  override val language = JavaLanguage.INSTANCE
}