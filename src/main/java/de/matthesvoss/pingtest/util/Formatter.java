package de.matthesvoss.pingtest.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Formatter {
    private static final DateTimeFormatter S_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter MS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Formatter() {
    }

    public static String formatTimeInterval(long timeIntervalMs) {
        Duration duration = Duration.ofMillis(timeIntervalMs);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatTimestampSec(long timestampMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault()).format(S_FORMAT);
    }

    public static String formatTimestampMs(long timestampMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault()).format(MS_FORMAT);
    }
}
