// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;

public class TagValueFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new TagValueFilter();

  private TagValueFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return false;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
