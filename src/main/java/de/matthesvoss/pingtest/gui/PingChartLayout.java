package de.matthesvoss.pingtest.gui;

import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.model.PingStatistics;

import java.awt.*;

class PingChartLayout {
    int plotLeft, plotRight, plotTop, plotBottom, plotW, plotH;
    long startTs, lastPingTs, plotTimeSpan;
    double xScale, yScale;
    FontMetrics fm;
    String yTop;
    int yLabelWidth;

    void compute(Graphics2D g2d, PingStatistics statistics, int width, int height) {
        PingResult worstPing = statistics.getWorst();
        int worst = worstPing == null || worstPing.isTimeout() ? -1 : worstPing.getRtt();

        fm = g2d.getFontMetrics();
        yTop = (worst <= 0 ? "0" : worst) + "ms";
        yLabelWidth = fm.stringWidth(yTop);
        int xLabelHeight = fm.getHeight();
        int leftMargin = Math.max(40, yLabelWidth + PingChart.Y_AXIS_PAD * 2);
        int bottomMargin = Math.max(28, xLabelHeight + PingChart.Y_AXIS_PAD * 2);
        int rightMargin = 12;
        int topMargin = 12;

        plotLeft = leftMargin;
        plotTop = topMargin;
        plotW = Math.max(0, width - leftMargin - rightMargin);
        plotH = Math.max(0, height - topMargin - bottomMargin);
        plotBottom = plotTop + plotH;
        plotRight = plotLeft + plotW;

        startTs = statistics.getStartOfFirstSession();
        lastPingTs = statistics.getTimestampOfLastPing();
        plotTimeSpan = Math.max(lastPingTs - startTs, 0L);

        xScale = plotTimeSpan > 0 ? (double) plotW / plotTimeSpan : 0.0;
        yScale = worst > 0 ? (double) plotH / worst : 0.0;
    }
}
