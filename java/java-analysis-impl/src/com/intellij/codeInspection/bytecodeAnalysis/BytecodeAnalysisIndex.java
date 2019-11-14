// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.KeyDescriptor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;


/**
 * @author lambdamix
 */
public class BytecodeAnalysisIndex extends ScalarIndexExtension<Object> {
  static final ID<Object, Void> NAME = ID.create("bytecodeAnalysis");

  @NotNull
  @Override
  public ID<Object, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<Object, Void, FileContent> getIndexer() {
    return inputData -> {
      try {
        return collectKeys(inputData.getContent());
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        // incorrect bytecode may result in Runtime exceptions during analysis
        // so here we suppose that exception is due to incorrect bytecode
        return Collections.emptyMap();
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<Object> getKeyDescriptor() {
    return null;
  }

  @NotNull
  private static Map<Object, Void> collectKeys(byte[] content) {
    HashMap<Object, Void> map = new HashMap<>();
    return map;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 11;
  }
}