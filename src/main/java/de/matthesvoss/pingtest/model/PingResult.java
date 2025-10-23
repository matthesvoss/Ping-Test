package de.matthesvoss.pingtest.model;

public class PingResult {
    private final long timestamp; // when the ping reply arrived
    private final int rtt; // round trip time in ms; -1 if lost
    private PingSession session;
    private int sequence; // session sequence number, 0-based

    public PingResult(long timestamp, int rtt) {
        this.timestamp = timestamp;
        this.rtt = rtt;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getRtt() {
        return rtt;
    }

    public boolean isTimeout() {
        return rtt < 0;
    }

    public PingSession getSession() {
        return session;
    }

    public void setSession(PingSession session) {
        this.session = session;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public boolean isFirstInSession() {
        return sequence == 0;
    }

    public boolean isLastInSession() {
        return sequence == session.getPings().size() - 1;
    }
}
