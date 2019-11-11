// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.search;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Gets notified when a component is found and should be highlighted.
 */
public interface ComponentHighligtingListener {
  Topic<ComponentHighligtingListener> TOPIC = Topic.create("highlightComponent", ComponentHighligtingListener.class);

  void highlight(@NotNull JComponent component);
}
