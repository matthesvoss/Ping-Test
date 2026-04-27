package de.matthesvoss.pingtest.model;

import de.matthesvoss.pingtest.util.MedianCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PingStatistics {
    private final List<PingSession> sessions = new ArrayList<>();
    private final MedianCalculator medianCalc;
    private int sent, received, lost;
    private int best = Integer.MAX_VALUE;
    private int worst = -1;
    private PingResult bestPing, worstPing, lastPing;
    private long runningSumOkPings;

    public PingStatistics(MedianCalculator medianCalc) {
        this.medianCalc = medianCalc;
    }

    public void startNewSession() {
        sessions.add(new PingSession(System.currentTimeMillis()));
    }

    public void endCurrentSession() {
        if (!sessions.isEmpty()) {
            PingSession currentSession = sessions.get(sessions.size() - 1);
            if (currentSession.hasPings()) {
                currentSession.endSession();
            } else {
                sessions.remove(currentSession);
            }
        }
    }

    public List<PingSession> getSessions() {
        return sessions;
    }

    public PingSession getCurrentSession() {
        return !sessions.isEmpty() ? sessions.get(sessions.size() - 1) : null;
    }

    public boolean isReset() {
        return sent == 0;
    }

    public void reset() {
        sent = received = lost = 0;
        runningSumOkPings = 0L;
        best = Integer.MAX_VALUE;
        worst = -1;
        bestPing = worstPing = lastPing = null;
        sessions.clear();
        medianCalc.clear();
    }

    public List<PingResult> getAllPings() {
        return sessions.stream()
                .flatMap(s -> s.getPings().stream())
                .collect(Collectors.toList());
    }

    public void addPing(PingResult ping) {
        getCurrentSession().addPing(ping);
        sent++;
        if (ping.isTimeout()) {
            lost++;
        } else {
            received++;
            int rtt = ping.getRtt();
            runningSumOkPings += rtt;
            if (rtt < best) {
                best = rtt;
                bestPing = ping;
            }
            if (rtt > worst) {
                worst = rtt;
                worstPing = ping;
            }
            medianCalc.add(rtt);
        }
        lastPing = ping;
    }

    public int getSent() {
        return sent;
    }

    public int getReceived() {
        return received;
    }

    public int getLost() {
        return lost;
    }

    public double getLossPercent() {
        return sent == 0 ? 0.0 : (lost * 100.0 / sent);
    }

    public PingResult getBest() {
        return bestPing;
    }

    public PingResult getWorst() {
        return worstPing;
    }

    public PingResult getLast() {
        return lastPing;
    }

    public int getAverage() {
        if (received == 0) {
            return 0;
        } else {
            return (int) Math.round((double) runningSumOkPings / received);
        }
    }

    public int getMedian() {
        return medianCalc.getMedian();
    }

    public List<Long> getSessionTimestamps() {
        ArrayList<Long> timestamps = new ArrayList<>();
        for (PingSession session : sessions) {
            timestamps.add(session.getStartTimestamp());
            if (session.hasStopped()) {
                timestamps.add(session.getStopTimestamp());
            }
        }
        return timestamps;
    }

    public long getStartOfFirstSession() {
        return sessions.isEmpty() ? 0 : sessions.get(0).getStartTimestamp();
    }

    public long getTimestampOfLastPing() {
        return lastPing == null ? 0 : lastPing.getTimestamp();
    }

    public long getElapsedTime() {
        long elapsed = 0L;
        for (PingSession session : sessions) {
            long end = session.hasStopped() ? session.getStopTimestamp() : System.currentTimeMillis();
            elapsed += end - session.getStartTimestamp();
        }
        return elapsed;
    }
}
