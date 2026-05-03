package de.matthesvoss.pingtest.gui;

import de.matthesvoss.pingtest.gui.theme.ThemeColors;
import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.model.PingSession;
import de.matthesvoss.pingtest.model.PingStatistics;
import de.matthesvoss.pingtest.model.PingStatisticsListener;
import de.matthesvoss.pingtest.util.Formatter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class PingChart extends JPanel implements PingStatisticsListener {
    static final int Y_AXIS_PAD = 8;
    private static final int X_AXIS_PAD = 2;
    private static final Stroke NORMAL_STROKE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke THIN_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    private static final Stroke DIVIDER_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            1f, new float[]{6f, 6f}, 0f);
    private static final int PING_RADIUS = 3;
    private static final int PING_TOOLTIP_RADIUS = 5;
    private static final int PING_TOOLTIP_OFFSET = 6;
    private static final int PING_HOVER_RADIUS = 30;
    private static final int TICK_SIZE = 4;
    private static final float X_LABEL_BLEND_ALPHA = 0.3f;
    private static final int X_LABEL_HOVER_Y_PAD = 4;
    private static final int X_LABEL_HOVER_X = 24;
    private static final int X_LABEL_PAD = 2;
    private final PingStatistics statistics;
    private final PingChartLayout layout = new PingChartLayout();
    private List<Long> xLabelTimestamps = new ArrayList<>();
    private PingResult hoveredPing;
    private int hoveredXLabel = -1;

    public PingChart(PingStatistics statistics) {
        this.statistics = statistics;
        statistics.addListener(this);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX(), y = e.getY();
                if (updateHoveredPing(x, y) || updateHoveredXLabel(x, y)) {
                    repaint();
                }
            }
        });
    }

    public void reset() {
        hoveredPing = null;
        repaint();
    }

    private long getMouseTs(int mouseX) {
        long mouseXms = Math.max(0L, Math.min(layout.plotTimeSpan,
                Math.round((mouseX - layout.plotLeft) / layout.xScale)));
        return layout.startTs + mouseXms;
    }

    private boolean updateHoveredPing(int mouseX, int mouseY) {
        List<PingResult> pings = statistics.getAllPings();
        if (pings.isEmpty() || layout.plotTimeSpan <= 0 || layout.plotW <= 0 || layout.plotH <= 0) {
            if (hoveredPing != null) {
                hoveredPing = null;
                return true;
            }
            return false;
        }

        // Convert hover pixel radius to time window
        long mouseTs = getMouseTs(mouseX);
        long dt = (long) Math.ceil(PING_HOVER_RADIUS / layout.xScale);
        long tMin = mouseTs - dt;
        long tMax = mouseTs + dt;

        // Binary search for time window bounds
        List<Long> pingTimestamps = pings.stream().map(PingResult::getTimestamp).collect(Collectors.toList());
        int lo = Collections.binarySearch(pingTimestamps, tMin);
        lo = lo >= 0 ? lo : Math.max(0, -lo - 2);
        int hi = Collections.binarySearch(pingTimestamps, tMax);
        hi = hi >= 0 ? hi : -hi - 1;

        PingResult closestPing = null;
        int smallestD2 = PING_HOVER_RADIUS * PING_HOVER_RADIUS;

        PingResult worstPing = statistics.getWorst();
        int worst = worstPing == null || worstPing.isTimeout() ? -1 : worstPing.getRtt();
        double y = layout.plotBottom - mouseY;

        for (int i = lo; i < hi; i++) {
            PingResult ping = pings.get(i);
            int val = ping.isTimeout() ? 0 : ping.getRtt();
            double valPixels = val * layout.yScale;

            // Quick reject on Y before computing pixel distance
            if (worst > 0 && Math.abs(valPixels - y) > PING_HOVER_RADIUS) {
                continue;
            }

            long ts = pingTimestamps.get(i);
            int px = layout.plotLeft + (int) Math.round((ts - layout.startTs) * layout.xScale);
            int py = layout.plotBottom - (int) Math.round(val * layout.yScale);
            int dx = px - mouseX;
            int dy = py - mouseY;
            int d2 = dx * dx + dy * dy;
            if (d2 < smallestD2) {
                smallestD2 = d2;
                closestPing = ping;
            }
        }

        if (hoveredPing != closestPing) {
            hoveredPing = closestPing;
            return true;
        }
        return false;
    }

    private boolean updateHoveredXLabel(int mouseX, int mouseY) {
        int xLabelTop = layout.plotBottom + TICK_SIZE;
        int xLabelBottom = xLabelTop + layout.fm.getHeight();

        if (statistics.isReset() || mouseY < xLabelTop - X_LABEL_HOVER_Y_PAD
                || mouseY > xLabelBottom + X_LABEL_HOVER_Y_PAD) {
            if (hoveredXLabel != -1) {
                hoveredXLabel = -1;
                return true;
            }
            return false;
        }

        int hi = Collections.binarySearch(xLabelTimestamps, getMouseTs(mouseX));
        hi = hi >= 0 ? hi : -hi - 1;
        int lo = Math.max(0, hi - 1);

        int closestLabel = -1;
        int closestDist = Integer.MAX_VALUE;

        for (int i = lo; i <= hi; i++) {
            long ts = xLabelTimestamps.get(i);

            int x = layout.plotLeft + (int) Math.round((ts - layout.startTs) * layout.xScale);
            int dist = Math.abs(mouseX - x);

            if (dist < closestDist && dist <= X_LABEL_HOVER_X) {
                closestDist = dist;
                closestLabel = i;
            }
        }

        if (hoveredXLabel != closestLabel) {
            hoveredXLabel = closestLabel;
            return true;
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            layout.compute(g2d, statistics, getWidth(), getHeight());
            if (layout.plotW == 0 || layout.plotH == 0) {
                drawAxes(g2d);
                drawAxesLabels(g2d);
                return;
            }

            drawBandsAndDividers(g2d);
            drawAxes(g2d);
            drawAxesLabels(g2d);
            if (statistics.isReset()) {
                return;
            }

            drawPingData(g2d);
            drawTooltip(g2d);
        } finally {
            g2d.dispose();
        }
    }

    private void drawBandsAndDividers(Graphics2D g2d) {
        // Draw section shading and vertical dividers
        List<PingSession> sessions = statistics.getSessions();
        if (sessions.size() > 1) {
            g2d.setColor(ThemeColors.stoppedBand());

            // Draw bands for stopped windows
            for (int i = 0; i < sessions.size() - 1; i++) {
                long stopTs = sessions.get(i).getStopTimestamp();
                long nextStartTs = sessions.get(i + 1).getStartTimestamp();
                int xStart = layout.plotLeft + (int) Math.round((stopTs - layout.startTs) * layout.xScale) + 1;
                int xEnd = layout.plotLeft + (int) Math.round((nextStartTs - layout.startTs) * layout.xScale);
                xStart = Math.max(layout.plotLeft, Math.min(layout.plotRight, xStart));
                xEnd = Math.max(layout.plotLeft, Math.min(layout.plotRight, xEnd));
                if (xEnd > xStart) {
                    g2d.fillRect(xStart, layout.plotTop, xEnd - xStart, layout.plotH);
                }
            }

            // Dashed vertical divider lines at each press time
            g2d.setColor(ThemeColors.divider());
            g2d.setStroke(DIVIDER_STROKE);

            for (int i = 0; i < sessions.size(); i++) {
                PingSession session = sessions.get(i);
                if (i != 0) {
                    drawDivider(g2d, session.getStartTimestamp());
                }
                if (i != sessions.size() - 1 && session.hasStopped()) {
                    drawDivider(g2d, session.getStopTimestamp());
                }
            }
        }
    }

    private void drawAxes(Graphics2D g2d) {
        // Draw axes (left Y-axis, bottom X-axis)
        g2d.setColor(ThemeColors.axis());
        g2d.setStroke(THIN_STROKE);
        g2d.drawLine(layout.plotLeft, layout.plotTop, layout.plotLeft, layout.plotBottom);
        g2d.drawLine(layout.plotLeft, layout.plotBottom, layout.plotRight, layout.plotBottom);

        // Draw ticks
        g2d.drawLine(layout.plotLeft, layout.plotTop, layout.plotLeft - TICK_SIZE, layout.plotTop);
        g2d.drawLine(layout.plotLeft, layout.plotBottom, layout.plotLeft - TICK_SIZE, layout.plotBottom);
        g2d.drawLine(layout.plotLeft, layout.plotBottom, layout.plotLeft, layout.plotBottom + TICK_SIZE);
        g2d.drawLine(layout.plotRight, layout.plotBottom, layout.plotRight, layout.plotBottom + TICK_SIZE);
    }

    private void drawAxesLabels(Graphics2D g2d) {
        // Top y-axis label
        int topLabelX = layout.plotLeft - Y_AXIS_PAD - layout.yTopLabelWidth;
        int topLabelY = layout.plotTop + layout.fm.getAscent();
        g2d.drawString(layout.yTopLabel, topLabelX, topLabelY);
        // Bottom y-axis label
        String yBottom = "0ms";
        int bottomLabelX = layout.plotLeft - Y_AXIS_PAD - layout.fm.stringWidth(yBottom);
        int bottomLabelY = layout.plotBottom - X_AXIS_PAD;
        g2d.drawString(yBottom, bottomLabelX, bottomLabelY);

        // X-axis labels
        int xLabelY = layout.plotBottom + layout.fm.getAscent() + TICK_SIZE;
        if (statistics.isReset()) {
            String text = "00:00:00";

            CenteredLabel leftLabel = new CenteredLabel(text, layout.plotLeft, xLabelY);
            CenteredLabel rightLabel = new CenteredLabel(text, Math.max(layout.plotLeft, layout.plotRight), xLabelY);

            leftLabel.draw(g2d, false);
            rightLabel.draw(g2d, false);
            return;
        }

        // Build labels
        CenteredLabel[] xLabels = new CenteredLabel[xLabelTimestamps.size()];
        for (int i = 0; i < xLabelTimestamps.size(); i++) {
            long ts = xLabelTimestamps.get(i);
            int xLabelX = layout.plotLeft + (int) Math.round((ts - layout.startTs) * layout.xScale);
            xLabels[i] = new CenteredLabel(Formatter.formatTimestampSec(ts), xLabelX, xLabelY);
        }

        // Detect overlaps
        for (int i = 0; i < xLabels.length; i++) {
            for (int j = i + 1; j < xLabels.length; j++) {
                CenteredLabel l1 = xLabels[i], l2 = xLabels[j];
                if (l1.right >= l2.left) {
                    l2.background = true;
                    if (i == hoveredXLabel || j == hoveredXLabel) {
                        l1.overlapsHovered = l2.overlapsHovered = true;
                    }
                } else {
                    break;
                }
            }
        }

        boolean firstOverlapsHovered = xLabels[0].overlapsHovered;
        boolean lastOverlapsHovered = xLabels[xLabels.length - 1].overlapsHovered;

        if (!lastOverlapsHovered) {
            // Move overlaps of last label to background to always make it visible
            CenteredLabel last = xLabels[xLabels.length - 1];
            for (int i = xLabels.length - 2; i >= 0; i--) {
                CenteredLabel l1 = xLabels[i];
                if (l1.right >= last.left) {
                    l1.background = true;
                } else {
                    break;
                }
            }
        }

        // Sort labels into background and foreground
        List<CenteredLabel> blendedLabels = new ArrayList<>(), opaqueLabels = new ArrayList<>();
        for (int i = 0; i < xLabels.length; i++) {
            if (i == hoveredXLabel || i == 0 && !firstOverlapsHovered || i == xLabels.length - 1 && !lastOverlapsHovered) {
                opaqueLabels.add(xLabels[i]);
                continue;
            }

            CenteredLabel l = xLabels[i];
            if (l.background || l.overlapsHovered) {
                blendedLabels.add(l);
                continue;
            }
            opaqueLabels.add(l);
        }

        Composite old = g2d.getComposite();
        Composite blend = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, X_LABEL_BLEND_ALPHA);

        // Draw background labels
        g2d.setComposite(blend);
        for (CenteredLabel l : blendedLabels) {
            l.draw(g2d, false);
        }

        // Draw foreground labels
        g2d.setComposite(old);
        for (CenteredLabel l : opaqueLabels) {
            l.draw(g2d, true);
        }
    }

    private void drawDivider(Graphics2D g2d, long ts) {
        int x = layout.plotLeft + (int) Math.round((ts - layout.startTs) * layout.xScale);
        x = Math.max(layout.plotLeft, Math.min(layout.plotRight, x));
        g2d.drawLine(x, layout.plotTop, x, layout.plotBottom);
    }

    private void drawPingData(Graphics2D g2d) {
        // Use LOD if there are more pings than pixels
        double timeBetweenPings = (double) statistics.getElapsedTime() / statistics.getSent();
        double timeBetweenPixels = (double) layout.plotTimeSpan / layout.plotW;
        boolean useLOD = timeBetweenPings < timeBetweenPixels;

        if (useLOD) {
            drawPingsLOD(g2d);
        } else {
            drawPingsExact(g2d);
        }
    }

    private void drawPingsLOD(Graphics2D g2d) {
        // Arrays per on-screen x pixel
        int len = layout.plotW + 1;
        int[] minVal = new int[len];
        int[] maxVal = new int[len];
        Set<Integer> timeouts = new HashSet<>();
        Map<Integer, PingResult> firstPings = new HashMap<>();

        Arrays.fill(minVal, Integer.MAX_VALUE);
        Arrays.fill(maxVal, Integer.MIN_VALUE);

        // Collect stats per pixel
        List<PingResult> pings = statistics.getAllPings();
        int lastIdx = -1;
        for (PingResult ping : pings) {
            int idx = (int) Math.round((ping.getTimestamp() - layout.startTs) * layout.xScale);
            idx = Math.max(0, Math.min(layout.plotW, idx));

            if (ping.isTimeout()) {
                timeouts.add(idx);
            } else {
                int val = ping.getRtt();
                if (val < minVal[idx]) {
                    minVal[idx] = val;
                }
                if (val > maxVal[idx]) {
                    maxVal[idx] = val;
                }
            }

            if (idx != lastIdx) {
                firstPings.put(idx, ping);
            }
            lastIdx = idx;
        }

        // Draw envelope (vertical segments per pixel) and centerline
        Path2D centerLinePath = new Path2D.Double();
        int runLen = 0;
        boolean lastHasTimeout = false;

        for (int i = 0; i < len; i++) {
            boolean hasData = minVal[i] != Integer.MAX_VALUE;
            boolean hasTimeout = timeouts.contains(i);
            if (!hasData && !hasTimeout) {
                continue;
            }

            // Draw vertical segment
            PingResult firstPing = firstPings.getOrDefault(i, null);
            boolean segmentStart = false;
            boolean segmentEnd = false;
            if (firstPing != null) {
                segmentStart = firstPing.isFirstInSession();
                segmentEnd = firstPing.isLastInSession() || i == len - 1;
            }
            if (segmentStart) {
                runLen = 0;
                lastHasTimeout = false;
            }
            int x = layout.plotLeft + i;
            if (hasData) {
                int y1 = layout.plotBottom - (int) Math.round(minVal[i] * layout.yScale);
                int y2 = layout.plotBottom - (int) Math.round(maxVal[i] * layout.yScale);

                y1 = Math.max(layout.plotTop, Math.min(layout.plotBottom, y1));
                y2 = Math.max(layout.plotTop, Math.min(layout.plotBottom, y2));

                g2d.setColor(ThemeColors.accent());
                if (runLen == 0 && segmentEnd) {
                    // Draw centerline instead
                    if (minVal[i] == maxVal[i]) {
                        g2d.fillOval(x - PING_RADIUS, y1 - PING_RADIUS, PING_RADIUS * 2, PING_RADIUS * 2);
                    } else {
                        g2d.setStroke(NORMAL_STROKE);
                        g2d.drawLine(x, y1, x, y2);
                    }
                } else {
                    g2d.setStroke(THIN_STROKE);
                    g2d.drawLine(x, y1, x, y2);
                }
            }
            // Draw centerline
            int yMid = 0;
            if (hasData) {
                double mid = (minVal[i] + maxVal[i]) / 2.0;
                yMid = layout.plotBottom - (int) Math.round(mid * layout.yScale);
            }
            if (hasTimeout || lastHasTimeout) {
                // Draw centerline in danger color
                g2d.setColor(ThemeColors.danger());
                if (runLen == 0 && segmentEnd) {
                    g2d.fillOval(x - PING_RADIUS, layout.plotBottom - PING_RADIUS, PING_RADIUS * 2, PING_RADIUS * 2);
                } else if (runLen != 0) {
                    g2d.setStroke(NORMAL_STROKE);
                    Point2D last = centerLinePath.getCurrentPoint();
                    int lastYMid = (int) Math.round(last.getY());
                    int y = hasTimeout ? layout.plotBottom : yMid;
                    g2d.drawLine(x - 1, lastYMid, x, y);
                    centerLinePath.moveTo(x, y);
                }
            } else {
                if (runLen == 0) {
                    centerLinePath.moveTo(x, yMid);
                } else {
                    centerLinePath.lineTo(x, yMid);
                }
            }

            runLen++;
            lastHasTimeout = hasTimeout;
        }

        g2d.setColor(ThemeColors.accent());
        g2d.setStroke(NORMAL_STROKE);
        g2d.draw(centerLinePath);
    }

    private void drawPingsExact(Graphics2D g2d) {
        Path2D pingPath = new Path2D.Double();
        boolean lastIsTimeout = false;
        int lastX = 0, lastY = 0;

        List<PingResult> pings = statistics.getAllPings();
        for (PingResult ping : pings) {
            long ts = ping.getTimestamp();
            int val = ping.isTimeout() ? 0 : ping.getRtt();

            int x = layout.plotLeft + (int) Math.round((ts - layout.startTs) * layout.xScale);
            x = Math.max(layout.plotLeft, Math.min(layout.plotRight, x));
            int y = layout.plotBottom - (int) Math.round(val * layout.yScale);
            y = Math.max(layout.plotTop, Math.min(layout.plotBottom, y));

            boolean timeout = ping.isTimeout();
            boolean segmentStart = ping.isFirstInSession();

            if (timeout || (lastIsTimeout && !segmentStart)) {
                g2d.setColor(ThemeColors.danger());
            } else {
                g2d.setColor(ThemeColors.accent());
            }

            // If the run has only a single point, draw it as a dot
            if (ping.getSession().isSingleton()) {
                g2d.fillOval(x - PING_RADIUS, y - PING_RADIUS, PING_RADIUS * 2, PING_RADIUS * 2);
            }
            if (timeout || (lastIsTimeout && !segmentStart)) {
                // Draw timeout segment in danger color
                if (ping.getSequence() != 0) {
                    g2d.setStroke(NORMAL_STROKE);
                    g2d.drawLine(lastX, lastY, x, y);
                    pingPath.moveTo(x, y);
                }
            } else {
                if (segmentStart) {
                    pingPath.moveTo(x, y);
                } else {
                    pingPath.lineTo(x, y);
                }
            }

            lastIsTimeout = timeout;
            lastX = x;
            lastY = y;
        }

        g2d.setColor(ThemeColors.accent());
        g2d.setStroke(NORMAL_STROKE);
        g2d.draw(pingPath);
    }

    private void drawTooltip(Graphics2D g2d) {
        if (hoveredPing == null) {
            return;
        }
        // Draw tooltip using hovered index
        long ts = hoveredPing.getTimestamp();
        int val = hoveredPing.isTimeout() ? 0 : hoveredPing.getRtt();
        int px = layout.plotLeft + (int) Math.round((ts - layout.startTs) * layout.xScale);
        int py = layout.plotBottom - (int) Math.round(val * layout.yScale);
        px = Math.min(layout.plotRight, px);
        py = Math.max(layout.plotTop, py);

        g2d.setColor(ThemeColors.axis());
        g2d.drawOval(px - PING_TOOLTIP_RADIUS, py - PING_TOOLTIP_RADIUS, PING_TOOLTIP_RADIUS * 2,
                PING_TOOLTIP_RADIUS * 2);
        g2d.setFont(g2d.getFont().deriveFont(Font.BOLD));
        String s = Formatter.formatTimestampMs(ts) + " " + (hoveredPing.isTimeout() ? "Request timed out" : val + "ms");
        int tx = px + PING_TOOLTIP_OFFSET;
        int ty = py - PING_TOOLTIP_OFFSET;

        if (tx > getWidth() - layout.fm.stringWidth(s)) {
            tx = px - layout.fm.stringWidth(s) - PING_TOOLTIP_OFFSET;
        }
        tx = Math.max(tx, layout.plotLeft);

        ty = Math.min(ty, layout.plotBottom);
        if (ty < layout.fm.getAscent()) {
            ty = py + layout.fm.getAscent() + PING_TOOLTIP_OFFSET;
        }

        g2d.drawString(s, tx, ty);
    }

    private void rebuildXLabelTimestamps() {
        xLabelTimestamps = statistics.getSessionTimestamps();
        // Remove last stop timestamp and replace it with the timestamp of the last ping
        if (!xLabelTimestamps.isEmpty() && xLabelTimestamps.size() % 2 == 0) {
            xLabelTimestamps.remove(xLabelTimestamps.size() - 1);
        }
        xLabelTimestamps.add(statistics.getTimestampOfLastPing());
    }

    @Override
    public void onStatisticsChanged() {
        rebuildXLabelTimestamps();
    }

    private class CenteredLabel {
        int x, y, left, right, width;
        String text;
        boolean background, overlapsHovered;

        CenteredLabel(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
            width = layout.fm.stringWidth(text);
            int hw = width / 2;
            left = x - hw;
            right = x + hw;
        }

        void draw(Graphics2D g2d, boolean clearBackground) {
            if (clearBackground) {
                Color prev = g2d.getColor();
                g2d.setColor(getBackground());
                g2d.fillRect(left - X_LABEL_PAD, y - layout.fm.getAscent(), width + X_LABEL_PAD * 2,
                        layout.fm.getHeight() + X_LABEL_PAD);
                g2d.setColor(prev);
            }
            g2d.drawLine(x, layout.plotBottom, x, layout.plotBottom + TICK_SIZE);
            g2d.drawString(text, left, y);
        }
    }
}
