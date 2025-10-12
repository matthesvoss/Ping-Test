package de.matthesvoss.pingtest.util;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

public final class Utils {
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private Utils() {}

    public static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    public static String formatTime(long timeMs) {
        long hours = timeMs / 3_600_000L;
        long minutes = (timeMs % 3_600_000L) / 60_000L;
        long seconds = (timeMs % 60_000L) / 1000L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
