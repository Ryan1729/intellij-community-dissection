// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import java.util.stream.Stream;

interface Result {
  /**
   * @return a stream of keys which should be solved to make this result final
   */
  default Stream<Object> dependencies() {
    return Stream.empty();
  }
}

