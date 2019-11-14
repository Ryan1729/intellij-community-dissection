// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.stream.Stream;

enum Value implements Result {
  Bot, NotNull, Null, True, False, Fail, Pure, Top;

  static Stream<Value> typeValues(Type type) {
    if (ASMUtils.isReferenceType(type)) return Stream.of(Null, NotNull);
    if (ASMUtils.isBooleanType(type)) return Stream.of(True, False);
    return Stream.empty();
  }
}