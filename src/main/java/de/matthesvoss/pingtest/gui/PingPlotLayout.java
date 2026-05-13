package de.matthesvoss.pingtest.gui;

import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.model.PingStatistics;

import java.awt.*;

class PingPlotLayout {
    int plotLeft, plotRight, plotTop, plotBottom, plotWidth, plotHeight;
    long startTs, lastPingTs, plotTimeSpan;
    double xScale, yScale;
    FontMetrics plainFm, boldFm;
    String yTopLabel;
    int yTopLabelWidth;
    int lastPingMaxWidth;

    void compute(Graphics2D g2d, PingStatistics statistics, int width, int height) {
        PingResult worstPing = statistics.getWorst();
        int worst = worstPing == null || worstPing.isTimeout() ? -1 : worstPing.getRtt();

        plainFm = g2d.getFontMetrics();
        boldFm = g2d.getFontMetrics(g2d.getFont().deriveFont(Font.BOLD));
        yTopLabel = (worst == -1 ? "0" : worst) + "ms";
        yTopLabelWidth = plainFm.stringWidth(yTopLabel);
        lastPingMaxWidth = boldFm.stringWidth("Timeout");
        int xLabelHeight = plainFm.getHeight();
        int leftMargin = yTopLabelWidth + PingPlot.BORDER_PAD * 2;
        int bottomMargin = xLabelHeight + PingPlot.TICK_SIZE + PingPlot.BORDER_PAD;
        int rightMargin = lastPingMaxWidth + PingPlot.BORDER_PAD * 2;
        int topMargin = PingPlot.BORDER_PAD;

        plotLeft = leftMargin;
        plotTop = topMargin;
        plotWidth = Math.max(0, width - leftMargin - rightMargin);
        plotHeight = Math.max(0, height - topMargin - bottomMargin);
        plotBottom = plotTop + plotHeight;
        plotRight = plotLeft + plotWidth;

        startTs = statistics.getStartOfFirstSession();
        lastPingTs = statistics.getTimestampOfLastPing();
        plotTimeSpan = Math.max(lastPingTs - startTs, 0L);

        xScale = plotTimeSpan > 0 ? (double) plotWidth / plotTimeSpan : 0.0;
        yScale = worst > 0 ? (double) plotHeight / worst : 0.0;
    }
}
