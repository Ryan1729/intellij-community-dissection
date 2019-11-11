// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.idea.SplashManager;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class CustomizeIDEWizardDialog extends DialogWrapper implements ActionListener {
  private static final String BUTTONS = "BUTTONS";
  private static final String NO_BUTTONS = "NO_BUTTONS";

  private final JButton mySkipButton = new JButton("Skip Remaining and Set Defaults");
  private final JButton myBackButton = new JButton("Back");
  private final JButton myNextButton = new JButton("Next");

  private final JBCardLayout myCardLayout = new JBCardLayout();
  private final List<AbstractCustomizeWizardStep> mySteps = new ArrayList<>();
  private int myIndex = 0;
  private final JBLabel myNavigationLabel = new JBLabel();
  private final JBLabel myHeaderLabel = new JBLabel();
  private final JBLabel myFooterLabel = new JBLabel();
  private final CardLayout myButtonWrapperLayout = new CardLayout();
  private final JPanel myButtonWrapper = new JPanel(myButtonWrapperLayout);
  private JPanel myContentPanel;
  private final boolean myHideSkipButton;

  public CustomizeIDEWizardDialog(@NotNull CustomizeIDEWizardStepsProvider stepsProvider) {
    this(stepsProvider, null, true, true);
  }

  public CustomizeIDEWizardDialog(@NotNull CustomizeIDEWizardStepsProvider stepsProvider, @Nullable StartupUtil.AppStarter appStarter,
                                  boolean beforeSplash, boolean afterSplash) {
    super(null, true, true);
    setTitle("Customize " + ApplicationNamesInfo.getInstance().getFullProductName());
    getPeer().setAppIcons();

    if (beforeSplash) stepsProvider.initSteps(mySteps);
    if (afterSplash) stepsProvider.initStepsAfterSplash(this, mySteps);

    if (appStarter != null) {
      int newIndex = appStarter.customizeIdeWizardDialog(mySteps);
      if (newIndex != -1) {
        myIndex = newIndex;
      }
    }

    myHideSkipButton = (mySteps.size() <= 1) || stepsProvider.hideSkipButton();

    if (mySteps.isEmpty()) {
      close(CANCEL_EXIT_CODE);
      return;
    }

    mySkipButton.addActionListener(this);
    myBackButton.addActionListener(this);
    myNextButton.addActionListener(this);
    AbstractCustomizeWizardStep.applyHeaderFooterStyle(myNavigationLabel);
    AbstractCustomizeWizardStep.applyHeaderFooterStyle(myHeaderLabel);
    AbstractCustomizeWizardStep.applyHeaderFooterStyle(myFooterLabel);
    init();
    initCurrentStep(true);
    setSize(400, 300);
    System.setProperty(StartupActionScriptManager.STARTUP_WIZARD_MODE, "true");
  }

  @Override
  public final void show() {
    if (mySteps.isEmpty()) {
      throw new IllegalStateException("no steps provided");  // use showIfNeeded() instead
    }
    SplashManager.executeWithHiddenSplash(getWindow(), () -> super.show());
  }

  public final boolean showIfNeeded() {
    final boolean willBeShown = !mySteps.isEmpty() && !isDisposed();
    if (willBeShown) {
      show();
    }
    return willBeShown;
  }

  @Override
  protected void dispose() {
    System.clearProperty(StartupActionScriptManager.STARTUP_WIZARD_MODE);
    super.dispose();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel result = new JPanel(new BorderLayout(5, 5));
    myContentPanel = new JPanel(myCardLayout);
    for (AbstractCustomizeWizardStep step : mySteps) {
      myContentPanel.add(step, step.getTitle());
    }
    JPanel topPanel = new JPanel(new BorderLayout(5, 5));
    if (mySteps.size() > 1) {
      topPanel.add(myNavigationLabel, BorderLayout.NORTH);
    }
    topPanel.add(myHeaderLabel, BorderLayout.CENTER);
    result.add(topPanel, BorderLayout.NORTH);
    result.add(myContentPanel, BorderLayout.CENTER);
    result.add(myFooterLabel, BorderLayout.SOUTH);
    result.setPreferredSize(JBUI.size(700, 600));
    result.setBorder(AbstractCustomizeWizardStep.createSmallEmptyBorder());
    return result;
  }

  @Override
  protected JComponent createSouthPanel() {
    final JPanel buttonPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets.right = 5;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0;
    gbc.gridy = 0;

    if (!myHideSkipButton)
      buttonPanel.add(mySkipButton, gbc);

    gbc.gridx++;
    buttonPanel.add(myBackButton, gbc);
    gbc.gridx++;
    gbc.weightx = 1;
    buttonPanel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridx++;
    gbc.weightx = 0;
    buttonPanel.add(myNextButton, gbc);
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    myButtonWrapper.add(buttonPanel, BUTTONS);
    myButtonWrapper.add(new JLabel(), NO_BUTTONS);
    myButtonWrapperLayout.show(myButtonWrapper, BUTTONS);
    return myButtonWrapper;
  }

  void setButtonsVisible(boolean visible) {
    myButtonWrapperLayout.show(myButtonWrapper, visible ? BUTTONS : NO_BUTTONS);
  }

  @Override
  public void actionPerformed(@NotNull ActionEvent e) {
    if (e.getSource() == mySkipButton) {
      doOKAction();
      return;
    }
    if (e.getSource() == myBackButton) {
      myIndex--;
      initCurrentStep(false);
      return;
    }
    if (e.getSource() == myNextButton) {
      if (myIndex >= mySteps.size() - 1) {
        doOKAction();
        return;
      }
      myIndex++;
      initCurrentStep(true);
    }
  }

  @Nullable
  @Override
  protected ActionListener createCancelAction() {
    return null;//Prevent closing by <Esc>
  }

  @Override
  public void doCancelAction() {
    doOKAction();
  }

  @Override
  protected void doOKAction() {
    for (AbstractCustomizeWizardStep step : mySteps) {
      if (!step.beforeOkAction()) {
        int index = mySteps.indexOf(step);
        if (myIndex != index) {
          myIndex = index;
          initCurrentStep(true);
        }
        return;
      }
    }
    super.doOKAction();
  }

  @Override
  protected boolean canRecordDialogId() {
    return false;
  }

  private void initCurrentStep(boolean forward) {
    final AbstractCustomizeWizardStep myCurrentStep = mySteps.get(myIndex);
    myCurrentStep.beforeShown();
    myCardLayout.swipe(myContentPanel, myCurrentStep.getTitle(), JBCardLayout.SwipeDirection.AUTO, () -> {
      Component component = myCurrentStep.getDefaultFocusedComponent();
      if (component != null) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
      }
    });

    myBackButton.setVisible(myIndex > 0);
    if (myIndex > 0) {
      myBackButton.setText("Back to " + mySteps.get(myIndex - 1).getTitle());
    }

    myNextButton.setText(myIndex < mySteps.size() - 1
                         ? "Next: " + mySteps.get(myIndex + 1).getTitle()
                         : "Start using " + ApplicationNamesInfo.getInstance().getFullProductName());
    myHeaderLabel.setText(ensureHTML(myCurrentStep.getHTMLHeader()));
    myFooterLabel.setText(ensureHTML(myCurrentStep.getHTMLFooter()));
    if (mySteps.size() > 1) {
      StringBuilder navHTML = new StringBuilder("<html><body>");
      String arrow = myNavigationLabel.getFont().canDisplay(0x2192) ? "&#8594;" : "&gt;";
      for (int i = 0; i < mySteps.size(); i++) {
        if (i > 0) navHTML.append("&nbsp;").append(arrow).append("&nbsp;");
        if (i == myIndex) navHTML.append("<b>");
        navHTML.append(mySteps.get(i).getTitle());
        if (i == myIndex) navHTML.append("</b>");
      }
      myNavigationLabel.setText(navHTML.toString());
    }
  }

  @Contract("!null->!null")
  private static String ensureHTML(@Nullable String s) {
    return s == null ? null : s.startsWith("<html>") ? s : "<html>" + StringUtil.escapeXmlEntities(s) + "</html>";
  }
}