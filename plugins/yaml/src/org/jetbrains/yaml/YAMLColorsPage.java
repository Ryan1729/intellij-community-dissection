// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author oleg
 */
public class YAMLColorsPage implements ColorSettingsPage {

  private static final String DEMO_TEXT = "---\n" +
                                          "# Read about fixtures at http://ar.rubyonrails.org/classes/Fixtures.html\n" +
                                          "static_sidebar:\n" +
                                          "  id: \"foo\"\n" +
                                          "  name: 'side_bar'\n" +
                                          "  staged_position: 1\n" +
                                          "  blog_id: 1\n" +
                                          "  config: |+\n" +
                                          "    --- !map:HashWithIndifferentAccess\n" +
                                          "      title: Static Sidebar\n" +
                                          "      body: The body of a static sidebar\n" +
                                          "  type: StaticSidebar\n" +
                                          "  description: >\n" +
                                          "    Sidebar configuration example\n" +
                                          "  extensions:\n" +
                                          "    - &params \n" +
                                          "        auto_run: true\n" +
                                          "        reload: true\n" +
                                          "    - *params";

  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[]{
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.key"), YAMLHighlighter.SCALAR_KEY),
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.string"), YAMLHighlighter.SCALAR_STRING),
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.dstring"), YAMLHighlighter.SCALAR_DSTRING),
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.scalar.list"), YAMLHighlighter.SCALAR_LIST),
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.scalar.text"), YAMLHighlighter.SCALAR_TEXT),
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.text"), YAMLHighlighter.TEXT),
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.sign"), YAMLHighlighter.SIGN),
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.anchor"), YAMLHighlighter.ANCHOR),
      new AttributesDescriptor(YAMLBundle.message("color.settings.yaml.comment"), YAMLHighlighter.COMMENT)
  };

  // Empty still
  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS = new HashMap<>();

  @Override
  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return YAMLBundle.message("color.settings.yaml.name");
  }

  @Override
  public Icon getIcon() {
    return YAMLFileType.YML.getIcon();
  }

  @Override
  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new YAMLSyntaxHighlighter();
  }

  @Override
  @NotNull
  public String getDemoText() {
    return DEMO_TEXT;
  }

}
