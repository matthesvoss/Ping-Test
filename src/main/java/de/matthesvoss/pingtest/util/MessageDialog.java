package de.matthesvoss.pingtest.util;

import javax.swing.*;
import java.awt.*;

public final class MessageDialog {
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

    public static void showError(Component parent, String message) {
        showError(parent, message, null);
    }

    public static void showError(Component parent, String message, Exception ex) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        parent,
                        message + (ex != null ? "\n" + ex.getMessage() : ""),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                )
        );
    }

    public static void show(Component parent, String message, MessageType type, Exception ex) {
        switch (type) {
            case INFO:
                showInfo(parent, message);
                break;
            case WARNING:
                showWarning(parent, message);
                break;
            case ERROR:
                showError(parent, message, ex);
                break;
        }
    }
}
