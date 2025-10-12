package de.matthesvoss.pingtest.service;

import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.util.MessageListener;
import de.matthesvoss.pingtest.util.MessageType;
import de.matthesvoss.pingtest.util.Utils;

import java.util.regex.Pattern;

public class PingParser {
    private final MessageListener messageListener;

    public PingParser(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    public PingResult parseLine(String line) {
        line = line.trim();
        long timestamp = System.currentTimeMillis();
        // Windows success lines ("Antwort", "Reply") and Linux/Unix success lines (contains "time=")
        if (line.startsWith("Antwort") || line.startsWith("Reply") || line.contains("time=")) {
            int rtt = parseLatency(line);
            return new PingResult(timestamp, rtt);
        }
        if (Pattern.compile("^Zeit.*berschreitung", Pattern.CASE_INSENSITIVE).matcher(line).find()
                || line.startsWith("PING: Fehler")
                || line.startsWith("Request timed out")
                || line.startsWith("PING: transmit failed")
                || line.toLowerCase().contains("timeout")
                || line.toLowerCase().contains("unreachable")
                || line.toLowerCase().contains("time to live exceeded")) {
            return new PingResult(timestamp, -1);
        }
        if (line.startsWith("Ping-Anforderung") || line.startsWith("Ping request")
                || line.contains("Name or service not known")) {
            messageListener.onMessage(line, MessageType.ERROR);
        }
        return null;
    }

    private int parseLatency(String line) {
        try {
            if (Utils.IS_WINDOWS) {
                if (line.contains("Zeit")) { // Windows DE
                    return Integer.parseInt(line.split("Zeit")[1].split("ms")[0].substring(1));
                }
                if (line.contains("time")) { // Windows EN
                    return Integer.parseInt(line.split("time")[1].split("ms")[0].substring(1));
                }
            } else if (line.contains("time=")) { // Linux
                String msStr = line.split("time=")[1].split(" ")[0];
                return (int) Math.round(Double.parseDouble(msStr));
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}
