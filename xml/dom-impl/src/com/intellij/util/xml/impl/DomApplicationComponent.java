// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.TypeChooserManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class DomApplicationComponent {
  private final MultiMap<String, DomFileMetaData> myRootTagName2FileDescription = MultiMap.createSet();
  private final Set<DomFileMetaData> myAcceptingOtherRootTagNamesDescriptions = new THashSet<>();
  private final ImplementationClassCache myCachedImplementationClasses = new ImplementationClassCache(DomImplementationClassEP.EP_NAME);
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();


  public DomApplicationComponent() {
    //noinspection deprecation
    for (DomFileDescription<?> description : DomFileDescription.EP_NAME.getExtensionList()) {
      registerFileDescription(description);
    }
    for (DomFileMetaData meta : DomFileMetaData.EP_NAME.getExtensionList()) {
      registerFileDescription(meta);
    }
  }

  public static DomApplicationComponent getInstance() {
    return ServiceManager.getService(DomApplicationComponent.class);
  }

  public synchronized int getCumulativeVersion(boolean forStubs) {
    int result = 0;
    for (DomFileMetaData meta : allMetas()) {
      if (forStubs) {
        if (meta.stubVersion != null) {
          result += meta.stubVersion;
          result += StringUtil.notNullize(meta.rootTagName).hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
        }
      }
      else {
        result += meta.domVersion;
        result += StringUtil.notNullize(meta.rootTagName).hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
      }
    }
    return result;
  }

  private Iterable<DomFileMetaData> allMetas() {
    return ContainerUtil.concat(myRootTagName2FileDescription.values(), myAcceptingOtherRootTagNamesDescriptions);
  }

  @Nullable
  public synchronized DomFileMetaData findMeta(DomFileDescription<?> description) {
    return ContainerUtil.find(allMetas(), m -> m.lazyInstance == description);
  }

  public synchronized Set<DomFileDescription<?>> getFileDescriptions(String rootTagName) {
    return ContainerUtil.map2Set(myRootTagName2FileDescription.get(rootTagName), DomFileMetaData::getDescription);
  }

  public synchronized Set<DomFileDescription<?>> getAcceptingOtherRootTagNameDescriptions() {
    return ContainerUtil.map2Set(myAcceptingOtherRootTagNamesDescriptions, DomFileMetaData::getDescription);
  }

  synchronized void registerFileDescription(DomFileDescription<?> description) {
    registerFileDescription(new DomFileMetaData(description));
    initDescription(description);
  }

  void registerFileDescription(DomFileMetaData meta) {
    if (StringUtil.isEmpty(meta.rootTagName)) {
      myAcceptingOtherRootTagNamesDescriptions.add(meta);
    } else {
      myRootTagName2FileDescription.putValue(meta.rootTagName, meta);
    }
  }

  void initDescription(DomFileDescription<?> description) {
    Map<Class<? extends DomElement>, Class<? extends DomElement>> implementations = description.getImplementations();
    for (final Map.Entry<Class<? extends DomElement>, Class<? extends DomElement>> entry : implementations.entrySet()) {
      registerImplementation(entry.getKey(), entry.getValue(), null);
    }

    myTypeChooserManager.copyFrom(description.getTypeChooserManager());
  }

  public final void registerImplementation(Class<? extends DomElement> domElementClass, Class<? extends DomElement> implementationClass,
                                           @Nullable final Disposable parentDisposable) {
    myCachedImplementationClasses.registerImplementation(domElementClass, implementationClass, parentDisposable);
  }

}
