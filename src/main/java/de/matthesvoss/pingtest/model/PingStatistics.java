package de.matthesvoss.pingtest.model;

import de.matthesvoss.pingtest.util.MedianCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PingStatistics {
    private int sent, received, lost;
    private int best = Integer.MAX_VALUE;
    private int worst = -1;
    private int last = -1;
    private long runningSumOkPings;
    private final List<PingSession> sessions = new ArrayList<>();
    private final MedianCalculator medianCalc;

    public PingStatistics(MedianCalculator medianCalc) {
        this.medianCalc = medianCalc;
    }

    public void startNewSession() {
        sessions.add(new PingSession(System.currentTimeMillis()));
    }

    public void endCurrentSession() {
        if (!sessions.isEmpty()){
            sessions.get(sessions.size() - 1).endSession();
        }
    }

    public List<PingSession> getSessions() {
        return sessions;
    }
    
    public boolean hasStatistics() {
        return sent != 0;
    }

    public PingSession getCurrentSession() {
        return !sessions.isEmpty() ? sessions.get(sessions.size() - 1) : null;
    }

    public List<PingResult> getAllPings() {
        return sessions.stream()
                .flatMap(s -> s.getPings().stream())
                .collect(Collectors.toList());
    }

    public void reset() {
        sent = received = lost = 0;
        runningSumOkPings = 0L;
        best = Integer.MAX_VALUE;
        worst = -1;
        last = -1;
        sessions.clear();
        medianCalc.clear();
    }

    public void addPing(PingResult ping) {
        if (sessions.isEmpty()) {
            startNewSession();
        }
        getCurrentSession().addPing(ping);
        sent++;
        if (ping.isTimeout()) {
            lost++;
            last = -1;
        } else {
            received++;
            int rtt = ping.getRtt();
            runningSumOkPings += rtt;
            if (rtt < best) {
                best = rtt;
            }
            if (rtt > worst) {
                worst = rtt;
            }
            last = rtt;
            medianCalc.add(rtt);
        }
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

    public int getBest() {
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    public int getWorst() {
        return worst;
    }

    public int getLast() {
        return last;
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

    public long getStartOfFirstSession() {
        return sessions.isEmpty() ? 0 : sessions.get(0).getStartTimestamp();
    }

    public long getTimestampOfLastPing() {
        PingSession session = getCurrentSession();
        if (session == null) {
            return 0;
        }
        List<PingResult> pings = session.getPings();
        if (pings == null || pings.isEmpty()) {
            return 0;
        }
        return pings.get(pings.size() - 1).getTimestamp();
    }
}
