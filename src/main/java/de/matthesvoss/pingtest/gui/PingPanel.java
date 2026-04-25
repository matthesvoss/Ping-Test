package de.matthesvoss.pingtest.gui;

import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import de.matthesvoss.pingtest.controller.PingController;
import de.matthesvoss.pingtest.gui.theme.ThemeColors;
import de.matthesvoss.pingtest.gui.theme.ThemeManager;
import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.model.PingSession;
import de.matthesvoss.pingtest.model.PingStatistics;
import de.matthesvoss.pingtest.model.PreferencesManager;
import de.matthesvoss.pingtest.resources.ResourceLoader;
import de.matthesvoss.pingtest.service.PingProcessListener;
import de.matthesvoss.pingtest.service.exceptions.PingProcessException;
import de.matthesvoss.pingtest.service.exceptions.UnknownHostException;
import de.matthesvoss.pingtest.util.*;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class PingPanel extends JPanel implements ActionListener, PingProcessListener {
    private static final DecimalFormat LOSS_FORMAT = new DecimalFormat("0.00");
    private static final Stroke NORMAL_STROKE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke THIN_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    private static final Stroke DIVIDER_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            1f, new float[]{6f, 6f}, 0f);
    private static final int Y_AXIS_PAD = 8;
    private static final int PING_RADIUS = 3;
    private static final int PING_HOVER_RADIUS = 30;
    private static final int TICK_SIZE = 4;
    private static final float X_LABEL_BLEND_ALPHA = 0.3f;
    private static final int X_LABEL_HOVER_Y_PAD = 4;
    private static final int X_LABEL_HOVER_X = 24;
    private static final int X_LABEL_PAD = 2;
    private final PingController pingController;
    private final PingStatistics statistics = new PingStatistics(new MedianCalculator());
    private final JFrame frame = new JFrame("Ping Test");
    private final Timer elapsedTimer = new Timer(1000, e -> updateElapsedLabel());
    private final PreferencesManager prefs;
    private JLabel sentLabel, receivedLabel, lostLabel, lossLabel, bestLabel, averageLabel, medianLabel, worstLabel,
            lastLabel, elapsedLabel;
    private JTextField host;
    private JButton startStop, clear, share, theme;
    private JPopupMenu shareMenu;
    private JMenuItem copyStats, copyPings, copyScreenshot, saveScreenshot;
    private JSpinner count;
    private JCheckBox infinite;
    private MessageListener messageListener;
    private PingResult hoveredPing;
    private String lastHost = "";
    private int plotLeft, plotRight, plotTop, plotBottom, plotW, plotH;
    private long startTs, lastPingTs, plotTimeSpan, elapsedTime;
    private FontMetrics fm;
    private String yTop;
    private int yLabelWidth;
    private double xScale, yScale;
    private List<Long> xLabelTimestamps = new ArrayList<>();
    private int hoveredXLabel = -1;
    // TODO: separate labels further, add light colors to label backgrounds,
    //  add last ping to right side, end of ping spikes detection, ipv4/6

    public PingPanel(PingController pingController, PreferencesManager prefs) {
        this.pingController = pingController;
        this.prefs = prefs;
        LOSS_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    private static JPanel makeFlowGroup(LayoutManager layout, Component... components) {
        JPanel p = new JPanel(layout);
        for (Component c : components) {
            p.add(c);
        }
        return p;
    }

    public void createAndShow() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setIconImages(ResourceLoader.loadFrameIcons());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClosing();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX(), y = e.getY();
                if (updateHoveredPing(x, y) || updateHoveredXLabel(x, y)) {
                    repaint();
                }
            }
        });

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(createControlsPanel());
        topPanel.add(createStatsPanel());
        topPanel.add(new JSeparator(SwingConstants.HORIZONTAL));

        JPanel root = new JPanel(new BorderLayout());
        root.add(topPanel, BorderLayout.NORTH);
        root.add(this, BorderLayout.CENTER);
        frame.setContentPane(root);

        if (prefs.hasWindowBounds()) {
            frame.setLocation(prefs.getWindowX(frame.getX()), prefs.getWindowY(frame.getY()));
            frame.setSize(prefs.getWindowW(frame.getWidth()), prefs.getWindowH(frame.getHeight()));
            frame.setExtendedState(prefs.getWindowExtendedState(frame.getExtendedState()));
        } else {
            frame.pack();
        }

        frame.setVisible(true);
        startStop.requestFocusInWindow();
    }

    private JPanel createControlsPanel() {
        // Host and count controls
        JLabel hostLabel = new JLabel("Host:");
        host = new JTextField(prefs.getHost("google.com"), 20);
        JLabel countLabel = new JLabel("Count:");
        count = new JSpinner(new SpinnerNumberModel(10, 1, 86400, 1));
        count.setEnabled(false);
        infinite = new JCheckBox("Infinite", true);
        infinite.addActionListener(e -> count.setEnabled(!infinite.isSelected()));

        // Main buttons
        startStop = button("Start");
        frame.getRootPane().setDefaultButton(startStop);
        clear = button("Clear");

        // Right-side buttons
        rebuildShareMenu();
        share = scaledThemedIconButton("Share", "share", 1.0);
        // Show popup menu when Share button is clicked
        share.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    shareMenu.show(share, 0, share.getHeight());
                }
            }
        });

        theme = scaledThemedIconButton(ThemeManager.isDarkTheme() ? "Light mode" : "Dark mode", "theme", 1.0);

        JPanel leftGroup = makeFlowGroup(new FlowLayout(FlowLayout.LEFT, 6, 4), hostLabel, host, countLabel, count,
                infinite, startStop, clear);

        JPanel rightGroup = makeFlowGroup(new FlowLayout(FlowLayout.RIGHT, 6, 4), share, theme);

        // Controls panel (left/right in one row)
        JPanel controlsPanel = new JPanel(new BorderLayout());
        controlsPanel.add(leftGroup, BorderLayout.WEST);
        controlsPanel.add(rightGroup, BorderLayout.EAST);

        return controlsPanel;
    }

    private JPanel createStatsPanel() {
        // Stats labels
        sentLabel = new JLabel("Sent: ");
        receivedLabel = new JLabel("Received: ");
        lostLabel = new JLabel("Lost: ");
        lossLabel = new JLabel("Loss: %");
        bestLabel = new JLabel("Best: ms");
        averageLabel = new JLabel("Average: ms");
        medianLabel = new JLabel("Median: ms");
        worstLabel = new JLabel("Worst: ms");
        lastLabel = new JLabel("Last: ms");
        elapsedLabel = new JLabel("Elapsed: 00:00:00");

        return makeFlowGroup(new WrapLayout(FlowLayout.CENTER, 6, 0), sentLabel, receivedLabel, lostLabel, lossLabel,
                bestLabel, averageLabel, medianLabel, worstLabel, lastLabel, elapsedLabel);
    }

    private JButton button(String text) {
        JButton b = new JButton(text);
        b.addActionListener(this);
        b.setFocusPainted(false);
        return b;
    }

    private ScaledThemedIconButton scaledThemedIconButton(String text, String iconName, double heightFactor) {
        ScaledThemedIconButton b = new ScaledThemedIconButton(text, iconName, heightFactor);
        b.addActionListener(this);
        b.setFocusPainted(false);
        return b;
    }

    private JMenuItem menuItem(String text) {
        JMenuItem m = new JMenuItem(text);
        m.addActionListener(this);
        return m;
    }

    public JFrame getFrame() {
        return frame;
    }

    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    private void onWindowClosing() {
        pingController.stopPinging();
        prefs.setDarkTheme(ThemeManager.isDarkTheme());
        prefs.setHost(host.getText());
        prefs.putWindowBounds(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight(),
                frame.getExtendedState());
    }

    private long getMouseTs(int mouseX) {
        long mouseXms = Math.max(0L, Math.min(plotTimeSpan, Math.round((mouseX - plotLeft) / xScale)));
        return startTs + mouseXms;
    }

    private boolean updateHoveredPing(int mouseX, int mouseY) {
        List<PingResult> pings = statistics.getAllPings();
        if (pings.isEmpty() || plotTimeSpan <= 0 || plotW <= 0 || plotH <= 0) {
            if (hoveredPing != null) {
                hoveredPing = null;
                return true;
            }
            return false;
        }

        // Convert hover pixel radius to time window
        long mouseTs = getMouseTs(mouseX);
        long dt = (long) Math.ceil(PING_HOVER_RADIUS / xScale);
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
        double yScale = worst > 0 ? (double) plotH / worst : 0.0;
        double y = plotBottom - mouseY;

        for (int i = lo; i < hi; i++) {
            PingResult ping = pings.get(i);
            int val = ping.isTimeout() ? 0 : ping.getRtt();
            double valPixels = val * yScale;

            // Quick reject on Y before computing pixel distance
            if (worst > 0 && Math.abs(valPixels - y) > PING_HOVER_RADIUS) {
                continue;
            }

            long ts = pingTimestamps.get(i);
            int px = plotLeft + (int) Math.round((ts - startTs) * xScale);
            int py = plotBottom - (int) Math.round(val * yScale);
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
        int xLabelTop = plotBottom + TICK_SIZE;
        int xLabelBottom = xLabelTop + fm.getHeight();

        if (statistics.isReset() || mouseY < xLabelTop - X_LABEL_HOVER_Y_PAD
                || mouseY > xLabelBottom + X_LABEL_HOVER_Y_PAD) {
            if (hoveredXLabel != -1) {
                hoveredXLabel = -1;
                return true;
            }
            return false;
        }

        rebuildXLabelTimestamps();

        int hi = Collections.binarySearch(xLabelTimestamps, getMouseTs(mouseX));
        hi = hi >= 0 ? hi : -hi - 1;
        int lo = Math.max(0, hi - 1);

        int closestLabel = -1;
        int closestDist = Integer.MAX_VALUE;

        for (int i = lo; i <= hi; i++) {
            long ts = xLabelTimestamps.get(i);

            int x = plotLeft + (int) Math.round((ts - startTs) * xScale);
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
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(startStop)) {
            if (startStop.getText().equals("Start")) {
                startPinging();
                startStop.setText("Stop");
            } else {
                stopPinging();
                startStop.setText("Start");
            }
        } else if (e.getSource().equals(clear)) {
            boolean pinging = startStop.getText().equals("Stop");
            pingController.stopPinging();
            resetStats();
            resetLabels();
            if (pinging) {
                startPinging();
            }
        } else if (e.getSource().equals(copyStats)) {
            copyStatsToClipboard();
        } else if (e.getSource().equals(copyPings)) {
            copyPingsToClipboard();
        } else if (e.getSource().equals(copyScreenshot)) {
            copyScreenshotToClipboard();
        } else if (e.getSource().equals(saveScreenshot)) {
            saveScreenshot();
        } else if (e.getSource().equals(theme)) {
            switchTheme();
        }
    }

    private void startPinging() {
        if (!lastHost.isEmpty() && !host.getText().equals(lastHost)) {
            clear.doClick();
        }
        lastHost = host.getText();
        statistics.startNewSession();
        elapsedTimer.start();

        int countVal = -1;
        if (!infinite.isSelected()) {
            try {
                count.commitEdit();
            } catch (ParseException ex) {
                // Reset spinner to last valid value
                JComponent editor = count.getEditor();
                if (editor instanceof JSpinner.DefaultEditor) {
                    ((JSpinner.DefaultEditor) editor).getTextField().setValue(count.getValue());
                }
            }
            countVal = (int) count.getValue();
        }

        pingController.startPinging(host.getText(), countVal, this);
    }

    private void stopPinging() {
        statistics.endCurrentSession();
        elapsedTimer.stop();
        updateElapsedLabel();
        pingController.stopPinging();
    }

    private void updateElapsedLabel() {
        List<PingSession> sessions = statistics.getSessions();
        if (!sessions.isEmpty()) {
            long elapsedNew = 0L;
            for (PingSession session : sessions) {
                if (session.hasStopped()) {
                    elapsedNew += session.getStopTimestamp() - session.getStartTimestamp();
                } else {
                    elapsedNew += System.currentTimeMillis() - session.getStartTimestamp();
                }
            }
            elapsedLabel.setText("Elapsed: " + Utils.formatTime(elapsedNew));
            elapsedTime = elapsedNew;
        }
    }

    private void resetStats() {
        hoveredPing = null;
        statistics.reset();
        elapsedTimer.stop();
        repaint();
    }

    private void resetLabels() {
        sentLabel.setText("Sent: ");
        receivedLabel.setText("Received: ");
        lostLabel.setText("Lost: ");
        lossLabel.setText("Loss: %");
        bestLabel.setText("Best: ms");
        averageLabel.setText("Average: ms");
        medianLabel.setText("Median: ms");
        worstLabel.setText("Worst: ms");
        lastLabel.setText("Last: ms");
        elapsedLabel.setText("Elapsed: 00:00:00");
    }

    private void copyStatsToClipboard() {
        try {
            String stats = String.join("\t", sentLabel.getText(), receivedLabel.getText(), lostLabel.getText(),
                    lossLabel.getText(), bestLabel.getText(), averageLabel.getText(), medianLabel.getText(),
                    worstLabel.getText(), lastLabel.getText(), elapsedLabel.getText());
            Utils.copyToClipboard(stats);
        } catch (Exception ex) {
            messageListener.onMessage("Failed to copy statistics to clipboard", MessageType.ERROR, ex);
        }
    }

    private void copyPingsToClipboard() {
        try {
            ArrayList<String> lines = new ArrayList<>();
            lines.add(String.join("\t", sentLabel.getText(), receivedLabel.getText(), lostLabel.getText(),
                    lossLabel.getText(), bestLabel.getText(), averageLabel.getText(), medianLabel.getText(),
                    worstLabel.getText(), lastLabel.getText(), elapsedLabel.getText()));
            for (PingResult ping : statistics.getAllPings()) {
                String value = ping.isTimeout() ? "Request timed out" : ping.getRtt() + "ms";
                lines.add(Utils.formatTimestampMs(ping.getTimestamp()) + "\t" + value);
            }
            Utils.copyToClipboard(String.join("\n", lines));
        } catch (Exception ex) {
            messageListener.onMessage("Failed to copy pings to clipboard", MessageType.ERROR, ex);
        }
    }

    private void copyScreenshotToClipboard() {
        ScreenshotUtils.copyToClipboard(frame);
    }

    private void saveScreenshot() {
        BufferedImage img = ScreenshotUtils.takeScreenshot(frame);

        // Choose file destination
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Screenshot");
        chooser.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG Images (*.png)", "png");
        chooser.addChoosableFileFilter(pngFilter);
        chooser.setFileFilter(pngFilter);
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        chooser.setSelectedFile(new File("PingTest_" + ts + ".png"));

        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                ScreenshotUtils.saveScreenshot(img, file);
            } catch (Exception ex) {
                messageListener.onMessage("Failed to save screenshot", MessageType.ERROR, ex);
            }
        }
    }

    private void rebuildShareMenu() {
        shareMenu = new JPopupMenu();

        copyStats = menuItem("Copy Statistics");
        copyPings = menuItem("Copy All Pings");
        copyScreenshot = menuItem("Copy Screenshot");
        saveScreenshot = menuItem("Save Screenshot");

        shareMenu.add(copyStats);
        shareMenu.add(copyPings);
        shareMenu.add(copyScreenshot);
        shareMenu.add(saveScreenshot);
    }

    private void switchTheme() {
        FlatAnimatedLafChange.showSnapshot();

        ThemeManager.switchTheme();
        rebuildShareMenu();
        theme.setText(ThemeManager.isDarkTheme() ? "Light mode" : "Dark mode");

        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    @Override
    public void onPing(PingResult ping) {
        statistics.addPing(ping);
        updateStatsLabels();
        repaint();
    }

    private String formatPing(PingResult ping) {
        return (ping == null || ping.isTimeout() ? "-" : ping.getRtt()) + "ms";
    }

    private void updateStatsLabels() {
        sentLabel.setText("Sent: " + statistics.getSent());
        receivedLabel.setText("Received: " + statistics.getReceived());
        lostLabel.setText("Lost: " + statistics.getLost());
        double loss = statistics.getLossPercent();
        lossLabel.setText("Loss: " + LOSS_FORMAT.format(loss) + "%");
        bestLabel.setText("Best: " + formatPing(statistics.getBest()));
        worstLabel.setText("Worst: " + formatPing(statistics.getWorst()));
        averageLabel.setText("Average: " + statistics.getAverage() + "ms");
        medianLabel.setText("Median: " + statistics.getMedian() + "ms");
        lastLabel.setText("Last: " + formatPing(statistics.getLast()));
    }

    @Override
    public void onProcessException(PingProcessException ex) {
        if (ex instanceof UnknownHostException) {
            messageListener.onMessage("Unknown host: " + host.getText(), MessageType.ERROR);
            host.selectAll();
            host.requestFocus();
        } else {
            messageListener.onMessage(ex.getMessage(), MessageType.ERROR, ex.getCause());
        }
    }

    @Override
    public void onProcessFinished() {
        statistics.endCurrentSession();
        elapsedTimer.stop();
        updateElapsedLabel();

        startStop.setText("Start");
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1600, 800);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            computeVariables(g2d);
            if (plotW == 0 || plotH == 0) {
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

    private void computeVariables(Graphics2D g2d) {
        PingResult worstPing = statistics.getWorst();
        int worst = worstPing == null || worstPing.isTimeout() ? -1 : worstPing.getRtt();

        fm = g2d.getFontMetrics();
        yTop = (worst <= 0 ? "0" : worst) + "ms";
        yLabelWidth = fm.stringWidth(yTop);
        int xLabelHeight = fm.getHeight();
        int leftMargin = Math.max(40, yLabelWidth + Y_AXIS_PAD * 2);
        int bottomMargin = Math.max(28, xLabelHeight + Y_AXIS_PAD * 2);
        int rightMargin = 12;
        int topMargin = 12;

        plotLeft = leftMargin;
        plotTop = topMargin;
        plotW = Math.max(0, getWidth() - leftMargin - rightMargin);
        plotH = Math.max(0, getHeight() - topMargin - bottomMargin);
        plotBottom = plotTop + plotH;
        plotRight = plotLeft + plotW;

        startTs = statistics.getStartOfFirstSession();
        lastPingTs = statistics.getTimestampOfLastPing();
        plotTimeSpan = Math.max(lastPingTs - startTs, 0L);

        xScale = plotTimeSpan > 0 ? (double) plotW / plotTimeSpan : 0.0;
        yScale = worst > 0 ? (double) plotH / worst : 0.0;
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
                int xStart = plotLeft + (int) Math.round((stopTs - startTs) * xScale) + 1;
                int xEnd = plotLeft + (int) Math.round((nextStartTs - startTs) * xScale);
                xStart = Math.max(plotLeft, Math.min(plotRight, xStart));
                xEnd = Math.max(plotLeft, Math.min(plotRight, xEnd));
                if (xEnd > xStart) {
                    g2d.fillRect(xStart, plotTop, xEnd - xStart, plotH);
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
        g2d.drawLine(plotLeft, plotTop, plotLeft, plotBottom);
        g2d.drawLine(plotLeft, plotBottom, plotRight, plotBottom);

        // Draw ticks
        g2d.drawLine(plotLeft, plotTop, plotLeft - TICK_SIZE, plotTop);
        g2d.drawLine(plotLeft, plotBottom, plotLeft - TICK_SIZE, plotBottom);
        g2d.drawLine(plotLeft, plotBottom, plotLeft, plotBottom + TICK_SIZE);
        g2d.drawLine(plotRight, plotBottom, plotRight, plotBottom + TICK_SIZE);
    }

    private void drawAxesLabels(Graphics2D g2d) {
        // Top y-axis label
        int topLabelX = plotLeft - Y_AXIS_PAD - yLabelWidth;
        int topLabelY = plotTop + fm.getAscent();
        g2d.drawString(yTop, Math.max(2, topLabelX), Math.max(fm.getAscent(), topLabelY));
        // Bottom y-axis label
        String yBottom = "0ms";
        int bottomLabelX = plotLeft - Y_AXIS_PAD - fm.stringWidth(yBottom);
        int bottomLabelY = plotBottom - 2;
        g2d.drawString(yBottom, Math.max(2, bottomLabelX), bottomLabelY);

        // X-axis labels
        int xLabelY = plotBottom + fm.getAscent() + TICK_SIZE;
        if (statistics.isReset()) {
            String text = "00:00:00";

            CenteredLabel leftLabel = new CenteredLabel(text, plotLeft, xLabelY);
            CenteredLabel rightLabel = new CenteredLabel(text, Math.max(plotLeft, plotRight), xLabelY);

            leftLabel.draw(g2d, false);
            rightLabel.draw(g2d, false);
            return;
        }

        // Build labels
        rebuildXLabelTimestamps();
        CenteredLabel[] xLabels = new CenteredLabel[xLabelTimestamps.size()];
        for (int i = 0; i < xLabelTimestamps.size(); i++) {
            long ts = xLabelTimestamps.get(i);
            int xLabelX = plotLeft + (int) Math.round((ts - startTs) * xScale);
            xLabels[i] = new CenteredLabel(Utils.formatTimestampSec(ts), xLabelX, xLabelY);
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

    private void rebuildXLabelTimestamps() {
        xLabelTimestamps = statistics.getSessionTimestamps();
        // Remove last stop timestamp and replace it with the timestamp of the last ping
        if (!xLabelTimestamps.isEmpty() && xLabelTimestamps.size() % 2 == 0) {
            xLabelTimestamps.remove(xLabelTimestamps.size() - 1);
        }
        xLabelTimestamps.add(lastPingTs);
    }

    private void drawDivider(Graphics2D g2d, long ts) {
        int x = plotLeft + (int) Math.round((ts - startTs) * xScale);
        x = Math.max(plotLeft, Math.min(plotRight, x));
        g2d.drawLine(x, plotTop, x, plotBottom);
    }

    private void drawPingData(Graphics2D g2d) {
        // Use LOD if there are far more points than pixels
        boolean useLOD = elapsedTime / statistics.getSent() < plotTimeSpan / plotW;

        if (useLOD) {
            drawPingsLOD(g2d);
        } else {
            drawPingsExact(g2d);
        }
    }

    private void drawPingsLOD(Graphics2D g2d) {
        // Arrays per on-screen x pixel
        int len = plotW + 1;
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
            int idx = (int) Math.round((ping.getTimestamp() - startTs) * xScale);
            idx = Math.max(0, Math.min(plotW, idx));

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
            }
            int x = plotLeft + i;
            if (hasData) {
                int y1 = plotBottom - (int) Math.round(minVal[i] * yScale);
                int y2 = plotBottom - (int) Math.round(maxVal[i] * yScale);

                y1 = Math.max(plotTop, Math.min(plotBottom, y1));
                y2 = Math.max(plotTop, Math.min(plotBottom, y2));

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
                yMid = plotBottom - (int) Math.round(mid * yScale);
            }
            if (hasTimeout || (lastHasTimeout && !segmentStart)) {
                // Draw centerline in danger color
                g2d.setColor(ThemeColors.danger());
                if (runLen == 0 && segmentEnd) {
                    g2d.fillOval(x - PING_RADIUS, plotBottom - PING_RADIUS, PING_RADIUS * 2, PING_RADIUS * 2);
                } else if (runLen != 0) {
                    g2d.setStroke(NORMAL_STROKE);
                    Point2D last = centerLinePath.getCurrentPoint();
                    int lastYMid = (int) Math.round(last.getY());
                    int y = hasTimeout ? plotBottom : yMid;
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
        // Exact Path2D polyline (no decimation)
        Path2D pingPath = new Path2D.Double();
        boolean lastIsTimeout = false;
        int lastX = 0, lastY = 0;

        List<PingResult> pings = statistics.getAllPings();
        for (PingResult ping : pings) {
            long ts = ping.getTimestamp();
            int val = ping.isTimeout() ? 0 : ping.getRtt();

            int x = plotLeft + (int) Math.round((ts - startTs) * xScale);
            x = Math.max(plotLeft, Math.min(plotRight, x));
            int y = plotBottom - (int) Math.round(val * yScale);
            y = Math.max(plotTop, Math.min(plotBottom, y));

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
        int px = plotLeft + (int) Math.round((ts - startTs) * xScale);
        int py = plotBottom - (int) Math.round(val * yScale);
        px = Math.min(plotRight, px);
        py = Math.max(plotTop, py);

        g2d.setColor(ThemeColors.axis());
        g2d.drawOval(px - 5, py - 5, 10, 10);
        g2d.setFont(g2d.getFont().deriveFont(Font.BOLD));
        String s = Utils.formatTimestampMs(ts) + " " + (hoveredPing.isTimeout() ? "Request timed out" : val + "ms");
        int tx = px + 6;
        int ty = py - 6;

        if (tx > getWidth() - fm.stringWidth(s)) {
            tx = px - fm.stringWidth(s) - 6;
        }
        tx = Math.max(tx, plotLeft);

        ty = Math.min(ty, plotBottom);
        if (ty < fm.getAscent()) {
            ty = py + fm.getAscent() + 6;
        }

        g2d.drawString(s, tx, ty);
    }

    private class CenteredLabel {
        int x, y, left, right, width;
        String text;
        boolean background, overlapsHovered;

        CenteredLabel(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
            width = fm.stringWidth(text);
            int hw = width / 2;
            left = x - hw;
            right = x + hw;
        }

        void draw(Graphics2D g2d, boolean clearBackground) {
            if (clearBackground) {
                Color prev = g2d.getColor();
                g2d.setColor(getBackground());
                g2d.fillRect(left - X_LABEL_PAD, y - fm.getAscent(), width + X_LABEL_PAD * 2,
                        fm.getHeight() + X_LABEL_PAD);
                g2d.setColor(prev);
            }
            g2d.drawLine(x, plotBottom, x, plotBottom + TICK_SIZE);
            g2d.drawString(text, left, y);
        }
    }
}
