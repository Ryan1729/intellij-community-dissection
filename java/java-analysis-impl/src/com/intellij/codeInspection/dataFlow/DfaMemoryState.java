/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.codeInspection.dataFlow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DfaMemoryState {

  /**
   * Pops single value from the top of the stack and returns it
   * @return popped value
   * @throws java.util.EmptyStackException if stack is empty
   */
  @NotNull Object pop();

  /**
   * Reads a value from the top of the stack without popping it
   * @return top of stack value
   * @throws java.util.EmptyStackException if stack is empty
   */
  @NotNull Object peek();

  void push(@NotNull Object value);

  boolean applyCondition(Object dfaCond);

  /**
   * Returns true if given two values are known to be equal
   *
   * @param value1 first value to check
   * @param value2 second value to check
   * @return true if they are equal; false if not equal or not known
   */
  boolean areEqual(@NotNull Object value1, @NotNull Object value2);
  /**
   * Forces variable to have given fact (ignoring current value of this fact and flushing existing relations with this variable).
   * This might be useful if state is proven to be invalid, but we want to continue analysis to discover subsequent
   * problems under assumption that the state is still valid.
   * <p>
   *   E.g. if it's proven that nullable variable is dereferenced, for the sake of subsequent analysis one might call
   *   {@code forceVariableFact(var, NULLABILITY, NOT_NULL)}
   * </p>
   *
   * @param var the variable to modify
   * @param factType the type of the fact
   * @param value the new variable value
   * @param <T> type of fact value
   */
  <T> void forceVariableFact(@NotNull Object var, @NotNull Object factType, @Nullable T value);

  boolean isNull(Object dfaVar);

  boolean isNotNull(Object dfaVar);
}
