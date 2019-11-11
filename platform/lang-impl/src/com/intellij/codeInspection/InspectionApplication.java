// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.

import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.conversion.ConversionListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.containers.ConcurrentMultiMap;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

/**
 * @author max
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class InspectionApplication implements CommandLineInspectionProgressReporter {
  private static final Logger LOG = Logger.getInstance(InspectionApplication.class);

  public InspectionToolCmdlineOptionHelpProvider myHelpProvider;
  public String myProjectPath;
  public String myOutPath;
  public String mySourceDirectory;
  public String myStubProfile;
  public String myProfileName;
  public String myProfilePath;
  public boolean myRunWithEditorSettings;
  public boolean myRunGlobalToolsOnly;
  public boolean myAnalyzeChanges;
  private int myVerboseLevel;
  private final MultiMap<Pair<String, Integer>, String> originalWarnings = new ConcurrentMultiMap<>();
  public String myOutputFormat;

  public boolean myErrorCodeRequired = true;

  @NonNls public static final String DESCRIPTIONS = ".descriptions";
  @NonNls public static final String PROFILE = "profile";
  @NonNls public static final String INSPECTIONS_NODE = "inspections";
  @NonNls public static final String XML_EXTENSION = ".xml";

  public void startup() {
    if (myProjectPath == null) {
      reportError("Project to inspect is not defined");
      printHelp();
    }

    if (myProfileName == null && myProfilePath == null && myStubProfile == null) {
      reportError("Profile to inspect with is not defined");
      printHelp();
    }
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true);
    LOG.info("CPU cores: " + Runtime.getRuntime().availableProcessors() + "; ForkJoinPool.commonPool: " + ForkJoinPool.commonPool() + "; factory: " + ForkJoinPool.commonPool().getFactory());
    ApplicationManagerEx.getApplicationEx().setSaveAllowed(false);
    try {
      execute();
    }
    catch (Throwable e) {
      LOG.error(e);
      reportError(e.getMessage());
      gracefulExit();
      return;
    }

    if (myErrorCodeRequired) {
      ApplicationManagerEx.getApplicationEx().exit(true, true);
    }
  }

  public void execute() throws Exception {
    ApplicationManager.getApplication().runReadAction((ThrowableComputable<Object, Exception>)() -> {
      final ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
      reportMessageNoLineBreak(1, InspectionsBundle.message("inspection.application.starting.up",
                                              appInfo.getFullApplicationName() + " (build " + appInfo.getBuild().asString() + ")"));
      reportMessage(1, InspectionsBundle.message("inspection.done"));

      Disposable disposable = Disposer.newDisposable();
      try {
        run(Paths.get(FileUtil.toCanonicalPath(myProjectPath)), disposable);
      }
      finally {
        Disposer.dispose(disposable);
      }
      return null;
    });
  }

  private void printHelp() {
    assert myHelpProvider != null;

    myHelpProvider.printHelpAndExit();
  }

  private void run(@NotNull Path projectPath, @NotNull Disposable parentDisposable) throws IOException, JDOMException {
  }

  private void setupFirstAnalysisHandler(GlobalInspectionContextImpl context) {
    if (myVerboseLevel > 0) {
      reportMessage(1, "Running first analysis stage...");
    }
    context.setGlobalReportedProblemFilter(
      (entity, description) -> {
        if (!(entity instanceof RefElement)) return false;
        Pair<VirtualFile, Integer> fileAndLine = findFileAndLineByRefElement((RefElement) entity);
        if (fileAndLine == null) return false;
        originalWarnings.putValue(Pair.create(fileAndLine.first.getPath(), fileAndLine.second), description);
        return false;
      }
    );
    context.setReportedProblemFilter(
      (element, descriptors) -> {
        List<ProblemDescriptorBase> problemDescriptors = StreamEx.of(descriptors).select(ProblemDescriptorBase.class).toList();
        if (!problemDescriptors.isEmpty()) {
          ProblemDescriptorBase problemDescriptor = problemDescriptors.get(0);
          VirtualFile file = problemDescriptor.getContainingFile();
          if (file == null) return false;
          int lineNumber = problemDescriptor.getLineNumber();
          for (ProblemDescriptorBase it : problemDescriptors) {
            originalWarnings.putValue(Pair.create(file.getPath(), lineNumber), it.toString());
          }
        }
        return false;
      }
    );
  }

  @Nullable
  private static Pair<VirtualFile, Integer> findFileAndLineByRefElement(RefElement refElement) {
    PsiElement element = refElement.getPsiElement();
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return null;
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    int line = ReadAction.compute(() -> {
      Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
      return (document == null) ? -1 : document.getLineNumber(element.getTextRange().getStartOffset());
    });
    return Pair.create(virtualFile, line);
  }

  private void printBeforeSecondStageProblems() {
    if (myVerboseLevel == 3) {
      reportMessage(3, "Old warnings:");
      ArrayList<Map.Entry<Pair<String, Integer>, Collection<String>>> entries = new ArrayList<>(originalWarnings.entrySet());
      reportMessage(3, "total size: " + entries.size());
      entries.sort(Comparator.comparing((Map.Entry<Pair<String, Integer>, Collection<String>> o) -> o.getKey().first)
                     .thenComparingInt(o -> o.getKey().second));
      for (Map.Entry<Pair<String, Integer>, Collection<String>> entry : entries) {
        reportMessage(3, entry.getKey().first + ":" + (entry.getKey().second + 1));
        for (String value : entry.getValue()) {
          reportMessage(3, "\t\t" + value);
        }
      }
    }
  }

  private void gracefulExit() {
    if (myErrorCodeRequired) {
      System.exit(1);
    }
    else {
      throw new RuntimeException("Failed to proceed");
    }
  }

  @Nullable
  private InspectionProfileImpl loadInspectionProfile(@NotNull Project project) throws IOException, JDOMException {
    InspectionProfileImpl inspectionProfile = null;

    //fetch profile by name from project file (project profiles can be disabled)
    if (myProfileName != null) {
      inspectionProfile = loadProfileByName(project, myProfileName);
      if (inspectionProfile == null) {
        reportError("Profile with configured name (" + myProfileName + ") was not found (neither in project nor in config directory)");
        gracefulExit();
        return null;
      }
      return inspectionProfile;
    }

    if (myProfilePath != null) {
      inspectionProfile = loadProfileByPath(myProfilePath);
      if (inspectionProfile == null) {
        reportError("Failed to load profile from '" + myProfilePath + "'");
        gracefulExit();
        return null;
      }
      return inspectionProfile;
    }

    if (myStubProfile != null) {
      if (!myRunWithEditorSettings) {
        inspectionProfile = loadProfileByName(project, myStubProfile);
        if (inspectionProfile != null) return inspectionProfile;

        inspectionProfile = loadProfileByPath(myStubProfile);
        if (inspectionProfile != null) return inspectionProfile;
      }

      inspectionProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
      reportError("Using default project profile");
    }
    return inspectionProfile;
  }

  @Nullable
  private InspectionProfileImpl loadProfileByPath(@NotNull String profilePath) throws IOException, JDOMException {
    InspectionProfileImpl inspectionProfile = ApplicationInspectionProfileManager.getInstanceImpl().loadProfile(profilePath);
    if (inspectionProfile != null) {
      reportMessage(1, "Loaded profile '" + inspectionProfile.getName() + "' from file '" + profilePath + "'");
    }
    return inspectionProfile;
  }

  @Nullable
  private InspectionProfileImpl loadProfileByName(@NotNull Project project, @NotNull String profileName) {
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    InspectionProfileImpl inspectionProfile = profileManager.getProfile(profileName, false);
    if (inspectionProfile != null) {
      reportMessage(1, "Loaded shared project profile '" + profileName + "'");
    }
    else {
      //check if ide profile is used for project
      for (InspectionProfileImpl profile : profileManager.getProfiles()) {
        if (Comparing.strEqual(profile.getName(), profileName)) {
          inspectionProfile = profile;
          reportMessage(1, "Loaded local profile '" + profileName + "'");
          break;
        }
      }
    }

    return inspectionProfile;
  }


  private ConversionListener createConversionListener() {
    return new ConversionListener() {
      @Override
      public void conversionNeeded() {
        reportMessage(1, InspectionsBundle.message("inspection.application.project.has.older.format.and.will.be.converted"));
      }

      @Override
      public void successfullyConverted(@NotNull final File backupDir) {
        reportMessage(1, InspectionsBundle.message(
          "inspection.application.project.was.succesfully.converted.old.project.files.were.saved.to.0",
                                                  backupDir.getAbsolutePath()));
      }

      @Override
      public void error(@NotNull final String message) {
        reportError(InspectionsBundle.message("inspection.application.cannot.convert.project.0", message));
      }

      @Override
      public void cannotWriteToFiles(@NotNull final List<? extends File> readonlyFiles) {
        StringBuilder files = new StringBuilder();
        for (File file : readonlyFiles) {
          files.append(file.getAbsolutePath()).append("; ");
        }
        reportError(InspectionsBundle.message("inspection.application.cannot.convert.the.project.the.following.files.are.read.only.0", files.toString()));
      }
    };
  }

  @Nullable
  private static String getPrefix(final String text) {
    int idx = text.indexOf(" in ");
    if (idx == -1) {
      idx = text.indexOf(" of ");
    }

    return idx == -1 ? null : text.substring(0, idx);
  }

  public void setVerboseLevel(int verboseLevel) {
    myVerboseLevel = verboseLevel;
  }

  private void reportMessageNoLineBreak(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.print(message);
    }
  }

  @Override
  public void reportError(String message) {
    System.err.println(message);
  }

  @Override
  public void reportMessage(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.println(message);
    }
  }
}
