package de.matthesvoss.pingtest.util;

import javax.swing.*;
import java.awt.*;

public final class MessageDialog {
    private MessageDialog() {
    }

    public static void showInfo(Component parent, String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        parent,
                        message,
                        "Information",
                        JOptionPane.INFORMATION_MESSAGE
                )
        );
    }

    public static void showWarning(Component parent, String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        parent,
                        message,
                        "Warning",
                        JOptionPane.WARNING_MESSAGE
                )
        );
    }

    public static void showError(Component parent, String message, Throwable throwable) {
        if (message == null || message.isEmpty()) {
            message = "An error occurred";
        }
        if (throwable != null) {
            String exMessage = throwable.getMessage();
            if (exMessage != null && !exMessage.equalsIgnoreCase(message)) {
                message += "\n" + exMessage;
            }
        }
        String finalMessage = message;
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        parent,
                        finalMessage,
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                )
        );
    }

    public static void show(Component parent, String message, MessageType type, Throwable throwable) {
        switch (type) {
            case INFO:
                showInfo(parent, message);
                break;
            case WARNING:
                showWarning(parent, message);
                break;
            case ERROR:
                showError(parent, message, throwable);
                break;
        }
    }
}
