package de.matthesvoss.pingtest.service;

import de.matthesvoss.pingtest.Application;
import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.service.exceptions.PingProcessException;
import de.matthesvoss.pingtest.service.exceptions.UnknownHostException;
import de.matthesvoss.pingtest.service.results.ParsedIPAddress;
import de.matthesvoss.pingtest.service.results.ParsedLine;
import de.matthesvoss.pingtest.service.results.ParsedPingResult;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PingParser {
    // Windows
    private static final Pattern WIN_SUCCESS_EN = Pattern.compile("Reply from .*?time[=<]\\s*(\\d+(?:\\.\\d+)?)" +
            "\\s*ms", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIN_SUCCESS_DE = Pattern.compile("Antwort von .*?Zeit[=<]\\s*(\\d+(?:\\.\\d+)?)" +
            "\\s*ms", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIN_TIMEOUT = Pattern.compile("Zeit.*berschreitung|Request timed out|transmit " +
            "failed", Pattern.CASE_INSENSITIVE);

    // Linux/MacOS
    private static final Pattern LINUX_SUCCESS = Pattern.compile("icmp_seq=(\\d+).*?(?:time|Zeit)[=<]\\s*(\\d+(?:\\" +
            ".\\d+)?)\\s*ms", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINUX_TIMEOUT = Pattern.compile("timeout|unreachable|time to live exceeded",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LINUX_NO_ANSWER_YET = Pattern.compile("no answer yet for (?:icmp_)?seq=(\\d+)",
            Pattern.CASE_INSENSITIVE);

    // Host not found
    private static final Pattern HOST_NOT_FOUND = Pattern.compile("Ping request could not find host|Ping-Anforderung" +
                    " konnte Host|Name or service not known|nodename nor servname provided|bad address",
            Pattern.CASE_INSENSITIVE);

    // IP address
    private final Pattern IP_ADDRESS;

    // Track lost sequence numbers (Linux/MacOS only)
    private final Set<Integer> lostSeqs = new HashSet<>();

    PingParser(String host) {
        IP_ADDRESS = Pattern.compile(Pattern.quote(host) + " [\\[(]([^)\\]]+)[)\\]]");
    }

    public ParsedLine parseInputLine(String line) throws UnknownHostException {
        line = line.trim();
        if (line.isEmpty()) {
            return null;
        }

        long timestamp = System.currentTimeMillis();

        // Unknown host
        if (HOST_NOT_FOUND.matcher(line).find()) {
            throw new UnknownHostException(line);
        }

        // Handle "no answer yet for icmp_seq" (Linux/MacOS only)
        if (!Application.IS_WINDOWS) {
            Matcher noAns = LINUX_NO_ANSWER_YET.matcher(line);
            if (noAns.find()) {
                int seq = parseIntSafe(noAns.group(1));
                lostSeqs.add(seq);
                return new ParsedPingResult(new PingResult(timestamp, -1)); // treat as lost
            }
        }

        // Timeout / error lines
        if (WIN_TIMEOUT.matcher(line).find() || LINUX_TIMEOUT.matcher(line).find()) {
            return new ParsedPingResult(new PingResult(timestamp, -1));
        }

        // Success lines
        if (Application.IS_WINDOWS) {
            Matcher m = WIN_SUCCESS_EN.matcher(line);
            if (m.find()) {
                return new ParsedPingResult(new PingResult(timestamp, parseIntSafe(m.group(1))));
            }

            m = WIN_SUCCESS_DE.matcher(line);
            if (m.find()) {
                return new ParsedPingResult(new PingResult(timestamp, parseIntSafe(m.group(1))));
            }
        } else { // Linux/MacOS
            Matcher m = LINUX_SUCCESS.matcher(line);
            if (m.find()) {
                int seq = parseIntSafe(m.group(1));

                // Skip if seq is already marked as lost
                if (lostSeqs.contains(seq)) {
                    return null;
                }

                return new ParsedPingResult(new PingResult(timestamp, parseIntSafe(m.group(2))));
            }
        }

        Matcher m = IP_ADDRESS.matcher(line);
        if (m.find()) {
            return new ParsedIPAddress(m.group(1));
        }

        // Unrecognized line
        return null;
    }

    private int parseIntSafe(String s) {
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (Exception ex) {
            return -1;
        }
    }

    public void parseErrorLine(String line) throws PingProcessException {
        line = line.trim();
        if (HOST_NOT_FOUND.matcher(line).find()) {
            throw new UnknownHostException(line);
        }
        throw new PingProcessException(line);
    }
}
