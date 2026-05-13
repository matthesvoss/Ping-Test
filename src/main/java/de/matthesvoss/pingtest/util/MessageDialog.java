package de.matthesvoss.pingtest.util;

import javax.swing.*;
import java.awt.*;

public final class MessageDialog {
    private MessageDialog() {
    }

    public static void showInfo(Component parent, String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, message, "Information",
                JOptionPane.INFORMATION_MESSAGE));
    }

    public static void showWarning(Component parent, String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, message, "Warning",
                JOptionPane.WARNING_MESSAGE));
    }

    public static void showError(Component parent, String message, Throwable cause) {
        if (message == null || message.isEmpty()) {
            message = "An error occurred";
        }
        if (cause != null) {
            String exMessage = cause.getMessage();
            if (exMessage != null && !exMessage.equalsIgnoreCase(message)) {
                message += "\n" + exMessage;
            }
        }
        String finalMessage = message;
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, finalMessage, "Error",
                JOptionPane.ERROR_MESSAGE));
    }

    public static void show(Component parent, String message, MessageType type, Throwable cause) {
        switch (type) {
            case INFO:
                showInfo(parent, message);
                break;
            case WARNING:
                showWarning(parent, message);
                break;
            case ERROR:
                showError(parent, message, cause);
                break;
        }
    }
}
