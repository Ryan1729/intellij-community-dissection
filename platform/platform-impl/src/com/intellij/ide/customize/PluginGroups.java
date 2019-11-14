// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.ide.customize;

import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

public class PluginGroups {
  static final String CORE = "Core";
  private static final int MAX_DESCR_LENGTH = 55;

  static final String IDEA_VIM_PLUGIN_ID = "IdeaVIM";

  private final Map<String, Pair<Icon, List<String>>> myTree = new LinkedHashMap<>();
  private final Map<String, String> myFeaturedPlugins = new LinkedHashMap<>();

  private final Map<String, List<IdSet>> myGroups = new LinkedHashMap<>();
  private final Map<String, String> myDescriptions = new LinkedHashMap<>();
  private final List<IdeaPluginDescriptor> myPluginsFromRepository = new ArrayList<>();
  private final Collection<PluginId> myDisabledPluginIds = new HashSet<>();
  private final List<? extends IdeaPluginDescriptor> myAllPlugins;
  private boolean myInitialized;
  private Runnable myLoadingCallback;

  public PluginGroups() {
    myAllPlugins = PluginManagerCore.loadUncachedDescriptors();
    SwingWorker worker = new SwingWorker<List<IdeaPluginDescriptor>, Object>() {
      @Override
      protected List<IdeaPluginDescriptor> doInBackground() {
        try {
          return RepositoryHelper.loadPlugins(null);
        }
        catch (Exception e) {
          //OK, it's offline
          return Collections.emptyList();
        }
      }

      @Override
      protected void done() {
        try {
          myPluginsFromRepository.addAll(get());
          if (myLoadingCallback != null) myLoadingCallback.run();
        }
        catch (InterruptedException | ExecutionException e) {
          if (myLoadingCallback != null) myLoadingCallback.run();
        }
      }
    };
    worker.execute();
    PluginManagerCore.loadDisabledPlugins(new File(PathManager.getConfigPath()).getPath(), myDisabledPluginIds);

    initGroups(myTree, myFeaturedPlugins);
    initCloudPlugins();
  }

  void setLoadingCallback(Runnable loadingCallback) {
    myLoadingCallback = loadingCallback;
    if (!myPluginsFromRepository.isEmpty()) {
      myLoadingCallback.run();
    }
  }

  private void initCloudPlugins() {
    CloudConfigProvider provider = CloudConfigProvider.getProvider();
    if (provider == null) {
      return;
    }

    List<PluginId> plugins = provider.getInstalledPlugins();
    if (plugins.isEmpty()) {
      return;
    }

    for (Iterator<Entry<String, String>> I = myFeaturedPlugins.entrySet().iterator(); I.hasNext(); ) {
      String value = I.next().getValue();
      if (ContainerUtil.find(plugins, plugin -> value.endsWith(":" + plugin)) != null) {
        I.remove();
      }
    }

    for (PluginId plugin : plugins) {
      myFeaturedPlugins.put(plugin.getIdString(), "#Cloud:#Cloud:" + plugin);
    }
  }

  protected void
  initGroups(Map<String, Pair<Icon, List<String>>> tree, Map<String, String> featuredPlugins) {
    tree.put(CORE, Pair.create(null, Arrays.asList(
      "com.intellij.copyright",
      "com.intellij.java-i18n",
      "org.intellij.intelliLang",
      "com.intellij.properties",
      "Refactor-X",//?
      "Type Migration",
      "ZKM"
    )));
    initFeaturedPlugins(featuredPlugins);
  }

  protected void initFeaturedPlugins(@NotNull Map<String, String> featuredPlugins) {
    featuredPlugins.put("Scala", "Custom Languages:Plugin for Scala language support:org.intellij.scala");
    featuredPlugins.put("Live Edit Tool",
                        "Web Development:Provides live edit HTML/CSS/JavaScript:com.intellij.plugins.html.instantEditing");
    addVimPlugin(featuredPlugins);
    featuredPlugins.put("Atlassian Connector",
                        "Tools Integration:Integration for Atlassian JIRA, Bamboo, Crucible, FishEye:atlassian-idea-plugin");
    addTrainingPlugin(featuredPlugins);
  }

  public static void addVimPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("IdeaVim", "Editor:Emulates Vim editor:" + IDEA_VIM_PLUGIN_ID);
  }

  public static void addTrainingPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("IDE Features Trainer", "Code tools:Learn basic shortcuts and essential IDE features with quick interactive exercises:training");
  }

  public static void addRustPlugin(Map<String, String> featuredPlugins) {
    featuredPlugins.put("Rust", "Custom Languages:Rust language support:org.rust.lang");
  }

  private void initIfNeeded() {
    if (myInitialized) return;
    myInitialized = true;
    for (Entry<String, Pair<Icon, List<String>>> entry : myTree.entrySet()) {
      final String group = entry.getKey();
      if (CORE.equals(group)) continue;

      List<IdSet> idSets = new ArrayList<>();
      StringBuilder description = new StringBuilder();
      for (String idDescription : entry.getValue().getSecond()) {
        IdSet idSet = new IdSet(this, idDescription);
        String idSetTitle = idSet.getTitle();
        if (idSetTitle == null) continue;
        idSets.add(idSet);
        if (description.length() > 0) {
          description.append(", ");
        }
        description.append(idSetTitle);
      }
      myGroups.put(group, idSets);

      if (description.length() > MAX_DESCR_LENGTH) {
        int lastWord = description.lastIndexOf(",", MAX_DESCR_LENGTH);
        description.delete(lastWord, description.length()).append("...");
      }
      description.insert(0, "<html><body><center><i>");
      myDescriptions.put(group, description.toString());
    }
  }

  Map<String, Pair<Icon, List<String>>> getTree() {
    initIfNeeded();
    return myTree;
  }

  Map<String, String> getFeaturedPlugins() {
    initIfNeeded();
    return myFeaturedPlugins;
  }

  public String getDescription(String group) {
    initIfNeeded();
    return myDescriptions.get(group);
  }

  public List<IdSet> getSets(String group) {
    initIfNeeded();
    return myGroups.get(group);
  }

  @Nullable
  IdeaPluginDescriptor findPlugin(@NotNull PluginId id) {
    for (IdeaPluginDescriptor pluginDescriptor : myAllPlugins) {
      if (pluginDescriptor.getPluginId() == id) {
        return pluginDescriptor;
      }
    }
    return null;
  }

  boolean isIdSetAllEnabled(IdSet set) {
    for (PluginId id : set.getIds()) {
      if (!isPluginEnabled(id)) {
        return false;
      }
    }
    return true;
  }

  void setIdSetEnabled(@NotNull IdSet set, boolean enabled) {
    for (PluginId id : set.getIds()) {
      setPluginEnabledWithDependencies(id, enabled);
    }
  }

  @NotNull
  Collection<PluginId> getDisabledPluginIds() {
    return Collections.unmodifiableCollection(myDisabledPluginIds);
  }

  List<IdeaPluginDescriptor> getPluginsFromRepository() {
    return myPluginsFromRepository;
  }

  boolean isPluginEnabled(@NotNull PluginId pluginId) {
    initIfNeeded();
    return !myDisabledPluginIds.contains(pluginId);
  }

  private IdSet getSet(@NotNull PluginId pluginId) {
    initIfNeeded();
    for (List<IdSet> sets : myGroups.values()) {
      for (IdSet set : sets) {
        for (PluginId id : set.getIds()) {
          if (id == pluginId) {
            return set;
          }
        }
      }
    }
    return null;
  }

  void setPluginEnabledWithDependencies(@NotNull PluginId pluginId, boolean enabled) {
    initIfNeeded();
    Set<PluginId> ids = new HashSet<>();
    collectInvolvedIds(pluginId, enabled, ids);
    Set<IdSet> sets = new HashSet<>();
    for (PluginId id : ids) {
      IdSet set = getSet(id);
      if (set != null) {
        sets.add(set);
      }
    }
    for (IdSet set : sets) {
      ids.addAll(set.getIds());
    }
    for (PluginId id : ids) {
      if (enabled) {
        myDisabledPluginIds.remove(id);
      }
      else {
        myDisabledPluginIds.add(id);
      }
    }
  }

  private void collectInvolvedIds(PluginId pluginId, boolean toEnable, Set<PluginId> ids) {
    ids.add(pluginId);
    if (toEnable) {
      for (PluginId id : getNonOptionalDependencies(pluginId)) {
        collectInvolvedIds(id, true, ids);
      }
    }
    else {
      Condition<PluginId> condition = id -> pluginId == id;
      for (final IdeaPluginDescriptor plugin : myAllPlugins) {
        if (null != ContainerUtil.find(plugin.getDependentPluginIds(), condition) &&
            null == ContainerUtil.find(plugin.getOptionalDependentPluginIds(), condition)) {
          collectInvolvedIds(plugin.getPluginId(), false, ids);
        }
      }
    }
  }

  @NotNull
  private List<PluginId> getNonOptionalDependencies(PluginId id) {
    List<PluginId> result = new ArrayList<>();
    IdeaPluginDescriptor descriptor = findPlugin(id);
    if (descriptor != null) {
      for (PluginId pluginId : descriptor.getDependentPluginIds()) {
        if (pluginId == PluginManagerCore.CORE_ID) {
          continue;
        }
        if (!ArrayUtil.contains(pluginId, descriptor.getOptionalDependentPluginIds())) {
          result.add(pluginId);
        }
      }
    }
    return result;
  }
}
