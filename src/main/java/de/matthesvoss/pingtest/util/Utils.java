package de.matthesvoss.pingtest.util;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Utils {
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final DateFormat msFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final DateFormat sFormat = new SimpleDateFormat("HH:mm:ss");

    private Utils() {
    }

    public static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    public static String formatTime(long timeMs) {
        long hours = timeMs / 3_600_000L;
        long minutes = (timeMs % 3_600_000L) / 60_000L;
        long seconds = (timeMs % 60_000L) / 1000L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatTimestampMs(long timestampMs) {
        return msFormat.format(new Date(timestampMs));
    }

    public static String formatTimestampSec(long timestampMs) {
        return sFormat.format(new Date(timestampMs));
    }
}
