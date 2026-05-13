package de.matthesvoss.pingtest;

import de.matthesvoss.pingtest.controller.PingController;
import de.matthesvoss.pingtest.gui.PingPanel;
import de.matthesvoss.pingtest.gui.theme.ThemeManager;
import de.matthesvoss.pingtest.model.PreferencesManager;
import de.matthesvoss.pingtest.util.MessageDialog;
import de.matthesvoss.pingtest.util.MessageListener;

import javax.swing.*;

public class Application {
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public static void start() {
        SwingUtilities.invokeLater(Application::initUI);
    }

    private static void initUI() {
        PreferencesManager prefs = new PreferencesManager(Main.class);

        ThemeManager.init(prefs.isDarkTheme(true));

        PingController controller = new PingController();
        PingPanel gui = new PingPanel(controller, prefs);

        MessageListener messageListener = (msg, type, ex) -> MessageDialog.show(gui.getFrame(), msg, type, ex);
        gui.setMessageListener(messageListener);

        gui.createAndShow();
    }
}
