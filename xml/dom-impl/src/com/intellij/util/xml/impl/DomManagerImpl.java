// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.util.xml.impl;

import com.intellij.ide.highlighter.DomSupportEnabled;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.reference.SoftReference;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public final class DomManagerImpl extends DomManager {
  private static final Key<Object> MOCK = Key.create("MockElement");

  static final Key<WeakReference<DomFileElementImpl<?>>> CACHED_FILE_ELEMENT = Key.create("CACHED_FILE_ELEMENT");
  static final Key<DomFileDescription<?>> MOCK_DESCRIPTION = Key.create("MockDescription");
  private static final Key<CachedValue<DomFileElementImpl<?>>> FILE_ELEMENT_KEY = Key.create("DomFileElement");
  private static final Key<CachedValue<DomFileElementImpl<?>>> FILE_ELEMENT_KEY_FOR_INDEX = Key.create("DomFileElementForIndex");
  private static final Key<CachedValue<DomInvocationHandler>> HANDLER_KEY = Key.create("DomInvocationHandler");
  private static final Key<CachedValue<DomInvocationHandler>> HANDLER_KEY_FOR_INDEX = Key.create("DomInvocationHandlerForIndex");

  private final EventDispatcher<DomEventListener> myListeners = EventDispatcher.create(DomEventListener.class);

  private final Project myProject;
  private final DomApplicationComponent myApplicationComponent;

  private boolean myChanging;
  private boolean myBulkChange;

  public DomManagerImpl(Project project) {
    super(project);
    myProject = project;
    myApplicationComponent = DomApplicationComponent.getInstance();

    final PomModel pomModel = PomManager.getModel(project);
    pomModel.addModelListener(new PomModelListener() {
      @Override
      public void modelChanged(@NotNull PomModelEvent event) {
        if (myChanging) return;

        final XmlChangeSet changeSet = (XmlChangeSet)event.getChangeSet(pomModel.getModelAspect(XmlAspect.class));
        if (changeSet != null) {
          for (XmlFile file : changeSet.getChangedFiles()) {
            DomFileElementImpl<DomElement> element = getCachedFileElement(file);
            if (element != null) {
              fireEvent(new DomEvent(element, false));
            }
          }
        }
      }

      @Override
      public boolean isAspectChangeInteresting(@NotNull PomModelAspect aspect) {
        return aspect instanceof XmlAspect;
      }
    }, project);

    VirtualFileManager.getInstance().addAsyncFileListener(new AsyncFileListener() {
      @Nullable
      @Override
      public ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
        List<DomEvent> domEvents = new ArrayList<>();
        for (VFileEvent event : events) {
          if (shouldFireDomEvents(event)) {
            ProgressManager.checkCanceled();
            domEvents.addAll(calcDomChangeEvents(event.getFile()));
          }
        }
        return domEvents.isEmpty() ? null : new ChangeApplier() {
          @Override
          public void afterVfsChange() {
            fireEvents(domEvents);
          }
        };
      }

      private boolean shouldFireDomEvents(VFileEvent event) {
        if (event instanceof VFileContentChangeEvent) return !event.isFromSave();
        if (event instanceof VFilePropertyChangeEvent) {
          return VirtualFile.PROP_NAME.equals(((VFilePropertyChangeEvent)event).getPropertyName())
                 && !((VFilePropertyChangeEvent)event).getFile().isDirectory();
        }
        return event instanceof VFileMoveEvent || event instanceof VFileDeleteEvent;
      }
    }, myProject);
  }

  public long getPsiModificationCount() {
    return PsiManager.getInstance(getProject()).getModificationTracker().getModificationCount();
  }

  private List<DomEvent> calcDomChangeEvents(final VirtualFile file) {
    if (!(file instanceof NewVirtualFile) || myProject.isDisposed()) {
      return Collections.emptyList();
    }

    FileManager fileManager = PsiManagerEx.getInstanceEx(myProject).getFileManager();

    final List<DomEvent> events = new ArrayList<>();
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!file.isDirectory() && FileTypeRegistry.getInstance().isFileOfType(file, StdFileTypes.XML)) {
          PsiFile psiFile = fileManager.getCachedPsiFile(file);
          DomFileElementImpl<?> domElement = psiFile instanceof XmlFile ? getCachedFileElement((XmlFile)psiFile) : null;
          if (domElement != null) {
            events.add(new DomEvent(domElement, false));
          }
        }
        return true;
      }

      @Override
      public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        return ((NewVirtualFile)file).getCachedChildren();
      }
    });
    return events;
  }

  boolean isInsideAtomicChange() {
    return myBulkChange;
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static DomManagerImpl getDomManager(Project project) {
    return (DomManagerImpl)DomManager.getDomManager(project);
  }

  @Override
  public void addDomEventListener(DomEventListener listener, Disposable parentDisposable) {
    myListeners.addListener(listener, parentDisposable);
  }

  @Override
  public final ConverterManager getConverterManager() {
    return ServiceManager.getService(ConverterManager.class);
  }

  @Override
  public final ModelMerger createModelMerger() {
    return new ModelMergerImpl();
  }

  final void fireEvent(@NotNull DomEvent event) {
    if (isInsideAtomicChange()) return;
    clearCache();
    myListeners.getMulticaster().eventOccured(event);
  }

  private void fireEvents(@NotNull Collection<? extends DomEvent> events) {
    for (DomEvent event : events) {
      fireEvent(event);
    }
  }

  @Override
  public final DomGenericInfo getGenericInfo(final Type type) {
    return myApplicationComponent.getStaticGenericInfo(type);
  }

  @Nullable
  public static DomInvocationHandler getDomInvocationHandler(DomElement proxy) {
    if (proxy instanceof DomFileElement) {
      return null;
    }
    if (proxy instanceof DomInvocationHandler) {
      return (DomInvocationHandler)proxy;
    }
    return null;
  }

  @NotNull
  public static DomInvocationHandler getNotNullHandler(DomElement proxy) {
    DomInvocationHandler handler = getDomInvocationHandler(proxy);
    if (handler == null) {
      throw new AssertionError("null handler for " + proxy);
    }
    return handler;
  }

  public static StableInvocationHandler<?> getStableInvocationHandler() {
    return null;
  }

  public DomApplicationComponent getApplicationComponent() {
    return myApplicationComponent;
  }

  @Override
  public final Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(final XmlFile file, final Class<T> aClass, String rootTagName) {
    if (file.getUserData(MOCK_DESCRIPTION) == null) {
      file.putUserData(MOCK_DESCRIPTION, new MockDomFileDescription<>(aClass, rootTagName, file.getViewProvider().getVirtualFile()));
      clearCache();
    }
    final DomFileElementImpl<T> fileElement = getFileElement(file);
    assert fileElement != null;
    return fileElement;
  }

  public final Set<DomFileDescription<?>> getFileDescriptions(String rootTagName) {
    return myApplicationComponent.getFileDescriptions(rootTagName);
  }

  public final Set<DomFileDescription<?>> getAcceptingOtherRootTagNameDescriptions() {
    return myApplicationComponent.getAcceptingOtherRootTagNameDescriptions();
  }

  final void runChange(Runnable change) {
    final boolean b = setChanging(true);
    try {
      change.run();
    }
    finally {
      setChanging(b);
    }
  }

  final boolean setChanging(final boolean changing) {
    boolean oldChanging = myChanging;
    if (changing) {
      assert !oldChanging;
    }
    myChanging = changing;
    return oldChanging;
  }

  @Override
  @Nullable
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(@Nullable XmlFile file) {
    if (file == null || !(file.getFileType() instanceof DomSupportEnabled)) return null;
    //noinspection unchecked
    return (DomFileElementImpl<T>)CachedValuesManager.getCachedValue(file, chooseKey(FILE_ELEMENT_KEY, FILE_ELEMENT_KEY_FOR_INDEX), () ->
      CachedValueProvider.Result.create(DomCreator.createFileElement(file), PsiModificationTracker.MODIFICATION_COUNT, this));
  }

  private static <T> T chooseKey(T base, T forIndex) {
    return FileBasedIndex.getInstance().getFileBeingCurrentlyIndexed() != null ? forIndex : base;
  }

  @Nullable
  static <T extends DomElement> DomFileElementImpl<T> getCachedFileElement(@NotNull XmlFile file) {
    //noinspection unchecked
    return (DomFileElementImpl<T>)SoftReference.dereference(file.getUserData(CACHED_FILE_ELEMENT));
  }

  @Override
  @Nullable
  public final <T extends DomElement> DomFileElementImpl<T> getFileElement(XmlFile file, Class<T> domClass) {
    DomFileDescription<?> description = getDomFileDescription(file);
    if (description != null && myApplicationComponent.assignabilityCache.isAssignable(domClass, description.getRootElementClass())) {
      return getFileElement(file);
    }
    return null;
  }

  @Override
  @Nullable
  public final DomElement getDomElement(final XmlTag element) {
    if (myChanging) return null;

    final DomInvocationHandler handler = getDomHandler(element);
    return handler != null ? handler.getProxy() : null;
  }

  @Override
  @Nullable
  public GenericAttributeValue<?> getDomElement(final XmlAttribute attribute) {
    if (myChanging) return null;

    DomInvocationHandler handler = getDomHandler(attribute);
    return handler == null ? null : (GenericAttributeValue<?>)handler.getProxy();
  }

  @Nullable
  public DomInvocationHandler getDomHandler(@Nullable XmlElement xml) {
    if (xml instanceof XmlTag) {
      return CachedValuesManager.getCachedValue(xml, chooseKey(HANDLER_KEY, HANDLER_KEY_FOR_INDEX), () ->
      {
        DomInvocationHandler handler = DomCreator.createTagHandler((XmlTag)xml);
        if (handler != null && handler.getXmlTag() != xml) {
          throw new AssertionError("Inconsistent dom, stub=" + handler.getStub());
        }
        return CachedValueProvider.Result.create(handler, PsiModificationTracker.MODIFICATION_COUNT, this);
      });
    }
    if (xml instanceof XmlAttribute) {
      return CachedValuesManager.getCachedValue(xml, chooseKey(HANDLER_KEY, HANDLER_KEY_FOR_INDEX), () ->
        CachedValueProvider.Result.create(DomCreator.createAttributeHandler((XmlAttribute)xml), PsiModificationTracker.MODIFICATION_COUNT, this));
    }
    return null;
  }

  @Override
  @Nullable
  public AbstractDomChildrenDescription findChildrenDescription(@NotNull final XmlTag tag, @NotNull final DomElement parent) {
    DomInvocationHandler parentHandler = getDomInvocationHandler(parent);
    assert parentHandler != null;
    return parentHandler.getGenericInfo().findChildrenDescription(parentHandler, tag);
  }

  @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
  @Nullable
  public final DomFileDescription<?> getDomFileDescription(PsiElement element) {
    if (element instanceof XmlElement) {
      final PsiFile psiFile = element.getContainingFile();
      if (psiFile instanceof XmlFile) {
        return getDomFileDescription((XmlFile)psiFile);
      }
    }
    return null;
  }

  @Override
  public final <T extends DomElement> T createMockElement(final Class<T> aClass, final Module module, final boolean physical) {
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(myProject).createFileFromText("a.xml", StdFileTypes.XML, "", 0, physical);
    file.putUserData(MOCK_ELEMENT_MODULE, module);
    file.putUserData(MOCK, new Object());
    return getFileElement(file, aClass, "I_sincerely_hope_that_nobody_will_have_such_a_root_tag_name").getRootElement();
  }

  @Override
  public final boolean isMockElement(DomElement element) {
    return DomUtil.getFile(element).getUserData(MOCK) != null;
  }

  @Override
  public final <T extends DomElement> T createStableValue(final Factory<? extends T> provider) {
    return createStableValue(provider, t -> t.isValid());
  }

  @Override
  public final <T> T createStableValue(final Factory<? extends T> provider, final Condition<? super T> validator) {
    final T initial = provider.create();
    assert initial != null;

    Set<Class<?>> intf = new HashSet<>();
    ContainerUtil.addAll(intf, initial.getClass().getInterfaces());
    intf.add(StableElement.class);
    //noinspection unchecked

    return (T)null;
  }

  @Override
  public final void registerFileDescription(DomFileDescription<?> description) {
    clearCache();

    myApplicationComponent.registerFileDescription(description);
  }

  @Override
  @NotNull
  public final DomElement getResolvingScope(GenericDomValue<?> element) {
    final DomFileDescription<?> description = DomUtil.getFileElement(element).getFileDescription();
    return description.getResolveScope(element);
  }

  @Override
  @NotNull
  public final DomElement getIdentityScope(DomElement element) {
    DomFileDescription<?> description = DomUtil.getFileElement(element).getFileDescription();
    return description.getIdentityScope(element);
  }

  @Override
  public TypeChooserManager getTypeChooserManager() {
    return myApplicationComponent.getTypeChooserManager();
  }

  void performAtomicChange(@NotNull Runnable change) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final boolean oldValue = myBulkChange;
    myBulkChange = true;
    try {
      change.run();
    }
    finally {
      myBulkChange = oldValue;
      if (!oldValue) {
        clearCache();
      }
    }

    if (!isInsideAtomicChange()) {
      clearCache();
    }
  }

  private void clearCache() {
    incModificationCount();
  }
}
