package de.matthesvoss.pingtest;

import de.matthesvoss.pingtest.controller.PingController;
import de.matthesvoss.pingtest.gui.PingPanel;
import de.matthesvoss.pingtest.util.MessageDialog;
import de.matthesvoss.pingtest.util.MessageListener;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        PingController controller = new PingController();
        PingPanel gui = new PingPanel(controller);

        MessageListener messageListener = (msg, type, ex) ->
                MessageDialog.show(gui.getFrame(), msg, type, ex);
        gui.setMessageListener(messageListener);

        SwingUtilities.invokeLater(gui::createAndShow);
    }
}