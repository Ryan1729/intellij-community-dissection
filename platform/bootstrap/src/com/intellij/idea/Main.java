// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.", "// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.\n//This file was modified, from the form JetBrains provided, by Ryan1729, at least in so far as this notice was added, possibly more.
package com.intellij.idea;

import com.intellij.ide.BootstrapClassLoaderUtil;
import com.intellij.openapi.application.PathManager;

import javax.swing.*;
import java.awt.*;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Properties;

public final class Main {
  public static final int RESTART_FAILED = 2;
  public static final int STARTUP_EXCEPTION = 3;
  public static final int JDK_CHECK_FAILED = 4;
  public static final int DIR_CHECK_FAILED = 5;
  public static final int INSTANCE_CHECK_FAILED = 6;
  public static final int PLUGIN_ERROR = 8;
  public static final int OUT_OF_MEMORY = 9;
  @SuppressWarnings("unused") public static final int UNSUPPORTED_JAVA_VERSION = 10;  // left for compatibility/reserved for future use
  public static final int PRIVACY_POLICY_REJECTION = 11;
  public static final int INSTALLATION_CORRUPTED = 12;
  public static final int ACTIVATE_WRONG_TOKEN_CODE = 13;
  public static final int ACTIVATE_LISTENER_NOT_INITIALIZED = 14;
  public static final int ACTIVATE_RESPONSE_TIMEOUT = 15;

  private static final String PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix";

  private static boolean hasGraphics = true;

  private Main() { }

  public static void main(String[] args) {
    try {
      PathManager.loadProperties();

      ClassLoader newClassLoader = BootstrapClassLoaderUtil.initClassLoader();
      Thread.currentThread().setContextClassLoader(newClassLoader);

      Class<?> klass = Class.forName("com.intellij.ide.plugins.MainRunner", true, newClassLoader);
      Method startMethod = klass.getMethod("start");
      startMethod.setAccessible(true);
      startMethod.invoke(null);
    }
    catch (Throwable t) {
      showMessage("Start Failed", t);
      System.exit(STARTUP_EXCEPTION);
    }
  }

  public static void showMessage(String title, Throwable t) {
    StringWriter message = new StringWriter();

    AWTError awtError = findGraphicsError(t);
    if (awtError != null) {
      message.append("Failed to initialize graphics environment\n\n");
      hasGraphics = false;
      t = awtError;
    }
    else {
      message.append("Internal error. Please refer to ");
      boolean studio = "AndroidStudio".equalsIgnoreCase(System.getProperty(PLATFORM_PREFIX_PROPERTY));
      message.append(studio ? "https://code.google.com/p/android/issues" : "http://jb.gg/ide/critical-startup-errors");
      message.append("\n\n");
    }

    t.printStackTrace(new PrintWriter(message));

    Properties sp = System.getProperties();
    String jre = sp.getProperty("java.runtime.version", sp.getProperty("java.version", "(unknown)"));
    String vendor = sp.getProperty("java.vendor", "(unknown vendor)");
    String arch = sp.getProperty("os.arch", "(unknown arch)");
    String home = sp.getProperty("java.home", "(unknown java.home)");
    message.append("\n-----\nJRE ").append(jre).append(' ').append(arch).append(" by ").append(vendor).append('\n').append(home);

    showMessage(title, message.toString(), true);
  }

  private static AWTError findGraphicsError(Throwable t) {
    while (t != null) {
      if (t instanceof AWTError) {
        return (AWTError)t;
      }
      t = t.getCause();
    }
    return null;
  }

  @SuppressWarnings({"UndesirableClassUsage", "UseOfSystemOutOrSystemErr"})
  public static void showMessage(String title, String message, boolean error) {
    PrintStream stream = error ? System.err : System.out;
    stream.println("\n" + title + ": " + message);

    boolean headless = !hasGraphics || GraphicsEnvironment.isHeadless();
    if (!headless) {
      try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
      catch (Throwable ignore) { }

      try {
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setText(message.replaceAll("\t", "    "));
        textPane.setBackground(UIManager.getColor("Panel.background"));
        textPane.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(
          textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        int maxHeight = Toolkit.getDefaultToolkit().getScreenSize().height / 2;
        int maxWidth = Toolkit.getDefaultToolkit().getScreenSize().width / 2;
        Dimension component = scrollPane.getPreferredSize();
        if (component.height > maxHeight || component.width > maxWidth) {
          scrollPane.setPreferredSize(new Dimension(Math.min(maxWidth, component.width), Math.min(maxHeight, component.height)));
        }

        int type = error ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE;
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), scrollPane, title, type);
      }
      catch (Throwable t) {
        stream.println("\nAlso, a UI exception occurred on an attempt to show the above message:");
        t.printStackTrace(stream);
      }
    }
  }
}