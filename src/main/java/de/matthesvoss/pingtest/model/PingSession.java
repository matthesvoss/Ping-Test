package de.matthesvoss.pingtest.model;

import java.util.ArrayList;
import java.util.List;

public class PingSession {
    private final long startTimestamp;
    private long stopTimestamp;
    private final List<PingResult> pings = new ArrayList<>();

    public PingSession(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public void addPing(PingResult ping) {
        ping.setSession(this);
        ping.setSequence(pings.size());
        pings.add(ping);
    }

    public void endSession() {
        this.stopTimestamp = System.currentTimeMillis();
    }

    public boolean hasStopped() {
        return stopTimestamp != 0;
    }

    public List<PingResult> getPings() {
        return pings;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getStopTimestamp() {
        return stopTimestamp;
    }

    public boolean hasPings() {
        return !pings.isEmpty();
    }

    public boolean isSingleton() {
        return pings.size() == 1;
    }
}
