// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package org.jetbrains.idea.devkit.module;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.*;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PluginModuleType extends ModuleType<PluginModuleBuilder> {

  @NonNls public static final String ID = "PLUGIN_MODULE";

  public PluginModuleType() {
    super(ID);
  }

  public static PluginModuleType getInstance() {
    return (PluginModuleType)ModuleTypeManager.getInstance().findByID(ID);
  }

  public static boolean isOfType(@NotNull Module module) {
    return get(module) instanceof PluginModuleType;
  }

  @Override
  @NotNull
  public PluginModuleBuilder createModuleBuilder() {
    return new PluginModuleBuilder();
  }

  @Override
  @NotNull
  public String getName() {
    return DevKitBundle.message("module.title");
  }

  @Override
  @NotNull
  public String getDescription() {
    return DevKitBundle.message("module.description");
  }

  @NotNull
  @Override
  public Icon getNodeIcon(boolean isOpened) {
    return AllIcons.Nodes.Plugin;
  }

  @Nullable
  public static Object getPluginXml(Module module) {
    if (module == null) return null;
    if (!isOfType(module)) {
      for (final ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (final SourceFolder folder : entry.getSourceFolders(JavaModuleSourceRootTypes.PRODUCTION)) {
          final VirtualFile file = folder.getFile();
          if (file == null) continue;

          final String packagePrefix = folder.getPackagePrefix();
          final String prefixPath = packagePrefix.isEmpty() ? "" :
                                    packagePrefix.replace('.', '/') + '/';

          final String relativePath = prefixPath + PluginDescriptorConstants.PLUGIN_XML_PATH;
          final VirtualFile pluginXmlVF = file.findFileByRelativePath(relativePath);
        }
      }

      return null;
    }

    return null;
  }

  public static boolean isPluginModuleOrDependency(@Nullable Module module) {
    if (module == null) return false;
    if (isOfType(module)) return true;
    return getCandidateModules(module).size() > 0;
  }

  public static List<Module> getCandidateModules(Module module) {
    if (PsiUtil.isIdeaProject(module.getProject())) {
      Set<Module> dependents = new HashSet<>();
      ModuleUtilCore.collectModulesDependsOn(module, dependents);
      return new ArrayList<>(dependents);
    }

    final Module[] modules = ModuleManager.getInstance(module.getProject()).getModules();
    final List<Module> candidates = new ArrayList<>(modules.length);
    final Set<Module> deps = new HashSet<>(modules.length);
    for (Module m : modules) {
      if (get(m) == getInstance()) {
        deps.clear();
        PluginBuildUtil.getDependencies(m, deps);

        if (deps.contains(module) && getPluginXml(m) != null) {
          candidates.add(m);
        }
      }
    }
    return candidates;
  }

  @Override
  public boolean isValidSdk(@NotNull final Module module, final Sdk projectSdk) {
    return JavaModuleType.isValidJavaSdk(module);
  }
}
