// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.documentation.AbstractExternalFilter;
import com.intellij.codeInsight.documentation.PlatformDocumentationUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class JavaDocExternalFilter extends AbstractExternalFilter {
  private PsiElement myElement;

  private static final ParseSettings ourPackageInfoSettings = new ParseSettings(
    Pattern.compile("package\\s+[^\\s]+\\s+description", Pattern.CASE_INSENSITIVE),
    Pattern.compile("START OF BOTTOM NAVBAR", Pattern.CASE_INSENSITIVE),
    true, false
  );

  private static final Pattern HREF_SELECTOR = Pattern.compile("<A.*?HREF=\"([^>\"]*)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern METHOD_HEADING = Pattern.compile("<H[34]>(.+?)</H[34]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private final RefConvertor[] myReferenceConverters = {
    new RefConvertor(HREF_SELECTOR) {
      @Override
      protected String convertReference(String root, String href) {
        if (BrowserUtil.isAbsoluteURL(href)) {
          return href;
        }
        String reference = JavaDocInfoGenerator.createReferenceForRelativeLink(href, myElement);
        if (reference == null) {
          if (href.startsWith("#")) {
            return root + href;
          }
          else {
            String nakedRoot = ourHtmlFileSuffix.matcher(root).replaceAll("/");
            return doAnnihilate(nakedRoot + href);
          }
        }
        else {
          return reference;
        }
      }
    }
  };

  public JavaDocExternalFilter() {
  }

  @Override
  protected RefConvertor[] getRefConverters() {
    return myReferenceConverters;
  }

  @Nullable
  public static String filterInternalDocInfo(String text) {
    return text == null ? null : PlatformDocumentationUtil.fixupText(text);
  }

  @Nullable
  @Override
  public String getExternalDocInfoForElement(@NotNull String docURL, PsiElement element) throws Exception {
    return null;
  }

  @NotNull
  @Override
  protected ParseSettings getParseSettings(@NotNull String url) {
    return url.endsWith(JavaDocumentationProvider.PACKAGE_SUMMARY_FILE) ? ourPackageInfoSettings : super.getParseSettings(url);
  }
}