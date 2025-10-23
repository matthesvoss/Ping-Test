package de.matthesvoss.pingtest.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import de.matthesvoss.pingtest.Main;
import de.matthesvoss.pingtest.controller.PingController;
import de.matthesvoss.pingtest.gui.theme.ThemeColors;
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
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class PingPanel extends JPanel implements ActionListener, PingProcessListener {
    private static final long serialVersionUID = 1L;
    private static final int HOVER_RADIUS = 30;
    private final PingController pingController;
    private final PingStatistics statistics = new PingStatistics(new MedianCalculator());
    private final JFrame frame = new JFrame("Ping Test");
    private JPanel controlsBar;
    private JLabel sentLabel, receivedLabel, lostLabel, lossLabel, bestLabel, averageLabel, medianLabel,
            worstLabel, lastLabel, elapsedLabel;
    private JTextField host;
    private JButton startStop, clear, share, theme;
    private JMenuItem copyStats, copyPings, copyScreenshot, saveScreenshot;
    private JSpinner count;
    private JCheckBox infinite;
    private final DecimalFormat lossFormat = new DecimalFormat("0.00");
    private final DateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private final Timer elapsedTimer = new Timer(1000, e -> updateElapsedLabel());
    private final PreferencesManager prefs = new PreferencesManager(Main.class);
    private final Stroke normalStroke = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private final Stroke thinStroke = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    private final Stroke dividerStroke = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            1f, new float[]{6f, 6f}, 0f);
    private final int padding = 8, r = 3;
    private MessageListener messageListener;
    private PingResult hoveredPing;
    private String lastHost = "";
    private boolean darkModeActive;
    private int plotLeft, plotRight, plotTop, plotBottom, plotW, plotH;
    private long startTs, plotTimeSpan, elapsedTime;
    private FontMetrics fm;
    private String yTop;
    private int yLabelWidth;
    private double xScale, yScale;
    // TODO: separate labels further, add light colors to label backgrounds,
    //  add last ping to right side, end of ping spikes detection,
    //  first ping on y axis and start and stop times on x axis, ipv4/6, light/dark theme icons,
    //  disable settings while pinging

    public PingPanel(PingController pingController) {
        this.pingController = pingController;
        lossFormat.setRoundingMode(RoundingMode.HALF_UP);
    }

    public void createAndShow() {
        // Initialize FlatLaf based on persisted preference before creating UI
        FlatLaf.registerCustomDefaultsSource("de.matthesvoss.pingtest.themes");
        darkModeActive = prefs.isDarkModeActive(false);
        if (darkModeActive) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

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
                updateHoveredPing(e.getX(), e.getY());
            }
        });

        JPanel buttonBar = createTopBar();
        JPanel statsPanel = createStatsPanel();

        // Stack buttons row and labels row in the main control bar
        controlsBar = new JPanel();
        controlsBar.setLayout(new BoxLayout(controlsBar, BoxLayout.Y_AXIS));
        controlsBar.add(buttonBar);
        controlsBar.add(statsPanel);

        // Keep the label group responsive to resizing
        controlsBar.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                statsPanel.revalidate();
                statsPanel.repaint();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.add(controlsBar, BorderLayout.NORTH);
        root.add(this, BorderLayout.CENTER);
        frame.setContentPane(root);

        reloadTheme(false);
        SwingUtilities.updateComponentTreeUI(frame);
        if (prefs.hasWindowBounds()) {
            frame.setLocation(prefs.getWindowX(frame.getX()), prefs.getWindowY(frame.getY()));
            frame.setSize(prefs.getWindowW(frame.getWidth()), prefs.getWindowH(frame.getHeight()));
            frame.setExtendedState(prefs.getWindowExtendedState(frame.getExtendedState()));
        } else {
            frame.pack();
        }
        frame.setVisible(true);
    }

    private JPanel createTopBar() {
        // Host and count controls
        JLabel hostLabel = new JLabel("Host:");
        host = new JTextField(prefs.getHost("google.de"), 20);
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
        share = button("Share");
        JPopupMenu shareMenu = new JPopupMenu();

        copyStats = menuItem("Copy Statistics");
        copyPings = menuItem("Copy All Pings");
        copyScreenshot = menuItem("Copy Screenshot");
        saveScreenshot = menuItem("Save Screenshot");

        shareMenu.add(copyStats);
        shareMenu.add(copyPings);
        shareMenu.add(copyScreenshot);
        shareMenu.add(saveScreenshot);
        // Show popup menu when Share button is clicked
        share.addActionListener(e -> shareMenu.show(share, 0, share.getHeight()));

        theme = button("Dark mode");

        JPanel leftGroup = makeFlowGroup(new FlowLayout(FlowLayout.LEFT, 6, 4),
                hostLabel, host, countLabel, count, infinite, startStop, clear);

        JPanel rightGroup = makeFlowGroup(new FlowLayout(FlowLayout.RIGHT, 6, 4),
                share, theme);

        // Button bar (left/right in one row)
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(leftGroup, BorderLayout.WEST);
        buttonBar.add(rightGroup, BorderLayout.EAST);

        return buttonBar;
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

        return makeFlowGroup(new WrapLayout(FlowLayout.CENTER, 6, 0),
                sentLabel, receivedLabel, lostLabel, lossLabel,
                bestLabel, averageLabel, medianLabel, worstLabel, lastLabel, elapsedLabel);
    }

    private JButton button(String text) {
        JButton b = new JButton(text);
        b.addActionListener(this);
        b.setFocusable(false);
        return b;
    }

    private JMenuItem menuItem(String text) {
        JMenuItem m = new JMenuItem(text);
        m.addActionListener(this);
        return m;
    }

    private static JPanel makeFlowGroup(LayoutManager layout, Component... components) {
        JPanel p = new JPanel(layout);
        for (Component c : components) {
            p.add(c);
        }
        return p;
    }

    public JFrame getFrame() {
        return frame;
    }

    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    private void onWindowClosing() {
        pingController.stopPinging();
        prefs.setDarkModeActive(darkModeActive);
        prefs.setHost(host.getText());
        prefs.putWindowBounds(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight(),
                frame.getExtendedState());
    }

    private void updateHoveredPing(int cursorPanelX, int cursorPanelY) {
        List<PingResult> pings = statistics.getAllPings();
        if (pings.isEmpty() || plotTimeSpan <= 0 || plotW <= 0 || plotH <= 0) {
            if (hoveredPing != null) {
                hoveredPing = null;
                repaint();
            }
            return;
        }

        double xScale = (double) plotW / plotTimeSpan;

        // Map cursor x to timestamp
        long cursorXms = Math.max(0L, Math.min(plotTimeSpan, Math.round((cursorPanelX - plotLeft) / xScale)));
        long cursorTs = startTs + cursorXms;

        // Convert hover pixel radius to time window
        long dt = (long) Math.ceil(HOVER_RADIUS / xScale);
        long tMin = cursorTs - dt;
        long tMax = cursorTs + dt;

        // Binary search for time window bounds
        List<Long> pingTimestamps = pings.stream().map(PingResult::getTimestamp).collect(Collectors.toList());
        int n = pingTimestamps.size();
        int lo = Collections.binarySearch(pingTimestamps, tMin);
        lo = (lo >= 0) ? lo : Math.max(0, Math.min(n, -lo - 2));
        int hi = Collections.binarySearch(pingTimestamps, tMax);
        hi = (hi >= 0) ? hi : Math.min(n, -hi - 1);

        PingResult closestPing = null;
        int smallestD2 = HOVER_RADIUS * HOVER_RADIUS;

        int worst = statistics.getWorst();
        double yScale = worst > 0 ? (double) plotH / worst : 0.0;
        double cursorY = plotBottom - cursorPanelY;

        for (int i = lo; i < hi; i++) {
            PingResult ping = pings.get(i);
            int val = ping.isTimeout() ? 0 : ping.getRtt();
            double valPixels = val * yScale;

            // Quick reject on Y (value-space) before computing pixel distance
            if (worst > 0 && Math.abs(valPixels - cursorY) > HOVER_RADIUS) {
                continue;
            }

            long ts = pingTimestamps.get(i);
            int px = plotLeft + (int) Math.round((ts - startTs) * xScale);
            int py = plotBottom - (int) Math.round(val * yScale);
            int dx = px - cursorPanelX;
            int dy = py - cursorPanelY;
            int d2 = dx * dx + dy * dy;
            if (d2 < smallestD2) {
                smallestD2 = d2;
                closestPing = ping;
            }
        }

        if (hoveredPing != closestPing) {
            hoveredPing = closestPing;
            repaint();
        }
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
            boolean running = startStop.getText().equals("Stop");
            pingController.stopPinging();
            resetStats();
            resetLabels();
            if (running) {
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
            darkModeActive = !darkModeActive;
            reloadTheme(true);
        }
    }

    private void startPinging() {
        if (!lastHost.isEmpty() && !host.getText().equals(lastHost)) {
            clear.doClick();
        }
        lastHost = host.getText();
        statistics.startNewSession();
        elapsedTimer.start();
        int countVal = infinite.isSelected() ? -1 : (int) count.getValue();
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
            String stats = String.join("\t", new String[]{
                    sentLabel.getText(), receivedLabel.getText(), lostLabel.getText(), lossLabel.getText(),
                    bestLabel.getText(), averageLabel.getText(), medianLabel.getText(), worstLabel.getText(),
                    lastLabel.getText(), elapsedLabel.getText()
            });
            Utils.copyToClipboard(stats);
        } catch (Exception ex) {
            messageListener.onMessage("Failed to copy statistics to clipboard", MessageType.ERROR, ex);
        }
    }

    private void copyPingsToClipboard() {
        try {
            ArrayList<String> lines = new ArrayList<>();
            lines.add(String.join("\t", new String[]{
                    sentLabel.getText(), receivedLabel.getText(), lostLabel.getText(), lossLabel.getText(),
                    bestLabel.getText(), averageLabel.getText(), medianLabel.getText(), worstLabel.getText(),
                    lastLabel.getText(), elapsedLabel.getText()
            }));
            for (PingResult ping : statistics.getAllPings()) {
                String value = ping.isTimeout() ? "Request timed out" : ping.getRtt() + "ms";
                lines.add(timestampFormat.format(new Date(ping.getTimestamp())) + "\t" + value);
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

    private void reloadTheme(boolean updateLaf) {
        try {
            if (updateLaf) {
                // Start animation capture
                FlatAnimatedLafChange.showSnapshot();
                if (darkModeActive) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
                FlatLaf.updateUI();
            }

            ThemeColors.refresh();
            controlsBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeColors.separator()));
            ImageIcon shareIcon = ResourceLoader.loadShareIcon(darkModeActive);
            share.setIcon(scaleIconToTextHeight(shareIcon, share));
            theme.setText(darkModeActive ? "Light mode" : "Dark mode");

            if (updateLaf) {
                // Finish animation transition
                FlatAnimatedLafChange.hideSnapshotWithAnimation();
            }
        } catch (Exception ex) {
            messageListener.onMessage("Failed to apply theme", MessageType.ERROR, ex);
        }
    }

    private static Icon scaleIconToTextHeight(ImageIcon icon, JButton button) {
        // Estimate target height based on font metrics
        FontMetrics fm = button.getFontMetrics(button.getFont());
        int textHeight = fm.getAscent();

        int iconWidth = icon.getIconWidth();
        int iconHeight = icon.getIconHeight();

        if (iconHeight == textHeight) {
            return icon;
        }

        float scale = (float) textHeight / iconHeight;
        int newW = Math.round(iconWidth * scale);
        int newH = Math.round(iconHeight * scale);

        Image image = icon.getImage();
        Image scaled = image.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    @Override
    public void onPing(PingResult ping) {
        statistics.addPing(ping);
        updateStatsLabels();
        repaint();
    }

    private void updateStatsLabels() {
        sentLabel.setText("Sent: " + statistics.getSent());
        receivedLabel.setText("Received: " + statistics.getReceived());
        lostLabel.setText("Lost: " + statistics.getLost());
        double loss = statistics.getLossPercent();
        lossLabel.setText("Loss: " + lossFormat.format(loss) + "%");
        bestLabel.setText("Best: " + statistics.getBest() + "ms");
        worstLabel.setText("Worst: " + statistics.getWorst() + "ms");
        averageLabel.setText("Average: " + statistics.getAverage() + "ms");
        medianLabel.setText("Median: " + statistics.getMedian() + "ms");
        lastLabel.setText("Last: " + statistics.getLast() + "ms");
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

            computePlotBounds(g2d);

            drawAxes(g2d);
            if (plotW == 0 || plotH == 0) {
                return;
            }
            drawBands(g2d);
            if (!statistics.hasStatistics()) {
                return;
            }
            drawPingData(g2d);
            drawTooltip(g2d);
        } finally {
            g2d.dispose();
        }
    }

    private void computePlotBounds(Graphics2D g2d) {
        fm = g2d.getFontMetrics();
        int worst = statistics.getWorst();
        yTop = (worst <= 0 ? "0" : worst) + "ms";
        yLabelWidth = fm.stringWidth(yTop);
        int xLabelHeight = fm.getHeight();
        int leftMargin = Math.max(40, yLabelWidth + padding * 2);
        int bottomMargin = Math.max(28, xLabelHeight + padding * 2);
        int rightMargin = 12;
        int topMargin = 12;

        plotLeft = leftMargin;
        plotTop = topMargin;
        plotW = Math.max(0, getWidth() - leftMargin - rightMargin);
        plotH = Math.max(0, getHeight() - topMargin - bottomMargin);
        plotBottom = plotTop + plotH;
        plotRight = plotLeft + plotW;
    }

    private void drawAxes(Graphics2D g2d) {
        // Draw axes (left Y-axis, bottom X-axis)
        g2d.setColor(ThemeColors.axis());
        g2d.setStroke(thinStroke);
        g2d.drawLine(plotLeft, plotTop, plotLeft, plotBottom);
        g2d.drawLine(plotLeft, plotBottom, plotRight, plotBottom);

        // Top y-axis label
        int topLabelX = plotLeft - padding - yLabelWidth;
        int topLabelY = plotTop + fm.getAscent();
        g2d.drawString(yTop, Math.max(2, topLabelX), Math.max(fm.getAscent(), topLabelY));
        // Bottom y-axis label
        String yBottom = "0ms";
        int bottomLabelX = plotLeft - padding - fm.stringWidth(yBottom);
        int bottomLabelY = plotBottom - 2;
        g2d.drawString(yBottom, Math.max(2, bottomLabelX), bottomLabelY);

        // X-axis labels
        startTs = statistics.getStartOfFirstSession();
        long lastTs = statistics.getTimestampOfLastPing();
        plotTimeSpan = Math.max(lastTs - startTs, 0L);

        String xLeft = "0s";
        String xRight = Utils.formatTime(plotTimeSpan);
        int xLeftY = plotBottom + fm.getAscent() + 4;
        g2d.drawString(xLeft, plotLeft, xLeftY);
        int xRightX = plotRight - fm.stringWidth(xRight);
        g2d.drawString(xRight, Math.max(plotLeft, xRightX), xLeftY);

        // Minor ticks and bands/dividers
        int tickSize = 4;
        g2d.drawLine(plotLeft, plotTop, plotLeft - tickSize, plotTop);
        g2d.drawLine(plotLeft, plotBottom, plotLeft - tickSize, plotBottom);
        g2d.drawLine(plotLeft, plotBottom, plotLeft, plotBottom + tickSize);
        g2d.drawLine(plotRight, plotBottom, plotRight, plotBottom + tickSize);
    }

    private void drawBands(Graphics2D g2d) {
        int worst = statistics.getWorst();
        xScale = plotTimeSpan > 0 ? (double) plotW / plotTimeSpan : 0.0;
        yScale = worst > 0 ? (double) plotH / worst : 0.0;

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
            g2d.setStroke(dividerStroke);

            for (int i = 1; i < sessions.size(); i++) {
                PingSession session = sessions.get(i);
                drawDivider(g2d, session.getStartTimestamp());
                if (i != sessions.size() - 1 || !session.hasStopped()) {
                    drawDivider(g2d, session.getStopTimestamp());
                }
            }

            // Redraw axes to keep them crisp
            g2d.setColor(ThemeColors.axis());
            g2d.setStroke(thinStroke);
            g2d.drawLine(plotLeft, plotTop, plotLeft, plotBottom);
            g2d.drawLine(plotLeft, plotBottom, plotRight, plotBottom);
        }
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
                if (runLen == 0 && segmentEnd) { // Draw centerline instead
                    if (minVal[i] == maxVal[i]) {
                        g2d.fillOval(x - r, y1 - r, r * 2, r * 2);
                    } else {
                        g2d.setStroke(normalStroke);
                        g2d.drawLine(x, y1, x, y2);
                    }
                } else {
                    g2d.setStroke(thinStroke);
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
                    g2d.fillOval(x - r, plotBottom - r, r * 2, r * 2);
                } else if (runLen != 0) {
                    g2d.setStroke(normalStroke);
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
        g2d.setStroke(normalStroke);
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
                g2d.fillOval(x - r, y - r, r * 2, r * 2);
            }
            if (timeout || (lastIsTimeout && !segmentStart)) {
                // Draw timeout segment in danger color
                if (ping.getSequence() != 0) {
                    g2d.setStroke(normalStroke);
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
        g2d.setStroke(normalStroke);
        g2d.draw(pingPath);
    }

    private void drawTooltip(Graphics2D g2d) {
        // Draw tooltip using hovered index
        if (hoveredPing != null) {
            long ts = hoveredPing.getTimestamp();
            int val = hoveredPing.isTimeout() ? 0 : hoveredPing.getRtt();
            int px = plotLeft + (int) Math.round((ts - startTs) * xScale);
            int py = plotBottom - (int) Math.round(val * yScale);
            px = Math.min(plotRight, px);
            py = Math.max(plotTop, py);

            g2d.setColor(ThemeColors.axis());
            g2d.drawOval(px - 5, py - 5, 10, 10);
            g2d.setFont(g2d.getFont().deriveFont(Font.BOLD));
            String s = timestampFormat.format(new Date(ts)) + " " + (hoveredPing.isTimeout() ?
                    "Request timed out" : val + "ms");
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
    }
}
