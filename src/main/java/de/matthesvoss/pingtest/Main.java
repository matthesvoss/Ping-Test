package de.matthesvoss.pingtest;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

public class Main extends JPanel implements ActionListener {
    private static final long serialVersionUID = 1L;
    private static final String PREF_DARK_MODE = "darkModeActive";
    private static final int HOVER_RADIUS = 30;
    private final boolean isWindows;
    private final JFrame frame;
    private final JPanel root, controlsBar, leftGroup, centerGroup, rightGroup, centerWrapper;
    private final JLabel hostLabel, countLabel, sentLabel, receivedLabel, lostLabel, lossLabel, bestLabel, averageLabel, medianLabel, worstLabel, lastLabel, elapsedLabel;
    private final JTextField host;
    private final JButton startStop, clear, copyResults, copyPings, screenshot, theme;
    private final JSpinner count;
    private final ArrayList<Long> pingTimestamps = new ArrayList<>();
    private final ArrayList<Integer> pingValues = new ArrayList<>();
    private final Set<Integer> lossIndices = new HashSet<>();
    private final ArrayList<Integer> breakIndices = new ArrayList<>();
    private final DecimalFormat lossFormat;
    private final DateFormat timestampFormat;
    private final ArrayList<Long> startStopTimestamps = new ArrayList<>();
    private final Timer elapsedTimer = new Timer(1000, e -> updateElapsedLabel());
    private final Preferences prefs = Preferences.userNodeForPackage(Main.class);
    private final PriorityQueue<Integer> medianLow = new PriorityQueue<>(Collections.reverseOrder());
    private final PriorityQueue<Integer> medianHigh = new PriorityQueue<>();
    private Process pingProcess;
    private SwingWorker<Void, String> pingProcessInputStreamTask, pingProcessErrorStreamTask;
    private int hoveredPingIndex = -1, sent, received, best = Integer.MAX_VALUE, average, worst = -1, last;
    private long runningSumOkPings;
    private String lastHost = "";
    private boolean darkModeActive;
    private volatile int runGeneration = 0; // Guards against out-of-order updates when rapidly starting/stopping
    private int plotLeft, plotTop, plotW, plotH;
    private long startTs = 0L, totalTime = 1L;
    private int median;
    // TODO: screenshot menu save or clipboard and save, separate labels further, add light colors to label backgrounds, add last ping to right side, end of ping spikes detection, change dark mode white to darker color

    private Color fgColor;          // primary foreground
    private Color gridColor;        // border/grid
    private Color seperatorColor;         // separator
    private Color accentColor;      // main accent for lines
    private Color dangerColor;      // timeouts / error ticks
    // Derived from the above to avoid recomputation in paint
    private Color axisColor;        // axis lines
    private Color stoppedBandColor; // shaded stopped sections
    private Color dividerColor;     // vertical divider lines

    private Main() {
        isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        // Initialize FlatLaf based on persisted preference before creating UI
        darkModeActive = prefs.getBoolean(PREF_DARK_MODE, false);
        if (darkModeActive) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        frame = new JFrame("Ping Test");
        frame.setSize(1600, 900);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        String[] resolutions = {"16", "32", "64", "128"};
        List<Image> icons = new ArrayList<>();
        for (String resolution : resolutions) {
            icons.add(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/icon" + resolution + ".png")));
        }
        frame.setIconImages(icons);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (pingProcess != null && pingProcess.isAlive()) {
                    stopPingProcess();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (pingTimestamps.isEmpty() || totalTime <= 0 || plotW <= 0 || plotH <= 0) {
                    if (hoveredPingIndex != -1) {
                        hoveredPingIndex = -1;
                        repaint();
                    }
                    return;
                }

                final int cx = e.getX();
                final int cy = e.getY();

                // Map cursor x to timestamp
                double fx = Math.max(0.0, Math.min(1.0, (double) (cx - plotLeft) / plotW));
                long cursorTs = startTs + Math.round(fx * totalTime);

                // Convert hover pixel radius to time window
                long dt = (long) Math.ceil((double) HOVER_RADIUS / plotW * totalTime);
                long tMin = cursorTs - dt;
                long tMax = cursorTs + dt;

                // Binary search for time window bounds
                int lo = Collections.binarySearch(pingTimestamps, tMin);
                lo = (lo >= 0) ? lo : Math.max(0, Math.min(pingTimestamps.size(), -lo - 2));
                int hi = Collections.binarySearch(pingTimestamps, tMax);
                hi = (hi >= 0) ? hi : Math.min(pingTimestamps.size(), -hi - 1);

                int bestIdx = -1;
                int bestDist2 = HOVER_RADIUS * HOVER_RADIUS;

                // Quick Y prune: convert vertical pixel radius to ms band and estimate cursor's ms
                double msPerPixel = (worst == 0) ? Double.POSITIVE_INFINITY : (double) worst / plotH;
                double dv = HOVER_RADIUS * msPerPixel;
                int plotBottom = plotTop + plotH;
                double cursorValMs = (worst == 0)
                        ? Double.POSITIVE_INFINITY
                        : ((double) worst * (plotBottom - cy)) / plotH;

                for (int i = lo; i < hi; i++) {
                    int val = pingValues.get(i);

                    // Quick reject on Y (value-space) before computing pixel distance
                    if (Math.abs(val - cursorValMs) > dv) {
                        continue;
                    }

                    long ts = pingTimestamps.get(i);
                    int px = plotLeft + (int) Math.round((double) (ts - startTs) / totalTime * plotW);
                    int py = plotBottom - (worst == 0 ? 0 : (int) Math.round((double) val / worst * plotH));
                    int dx = px - cx;
                    int dy = py - cy;
                    int d2 = dx * dx + dy * dy;
                    if (d2 < bestDist2) {
                        bestDist2 = d2;
                        bestIdx = i;
                    }
                }

                if (hoveredPingIndex != bestIdx) {
                    hoveredPingIndex = bestIdx;
                    repaint();
                }
            }
        });

        timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        lossFormat = new DecimalFormat("0.00");
        lossFormat.setRoundingMode(RoundingMode.HALF_UP);

        hostLabel = new JLabel("Host:");
        host = new JTextField("google.de", 20);
        countLabel = new JLabel("Count (0=infinity):");
        count = new JSpinner(new SpinnerNumberModel(0, 0, 86400, 1));
        startStop = new JButton("Start");
        startStop.addActionListener(this);
        startStop.setFocusable(false);
        SwingUtilities.getRootPane(frame).setDefaultButton(startStop);
        clear = new JButton("Clear");
        clear.addActionListener(this);
        clear.setFocusable(false);
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
        copyResults = new JButton("Copy Results");
        copyResults.addActionListener(this);
        copyResults.setFocusable(false);
        copyPings = new JButton("Copy Pings");
        copyPings.addActionListener(this);
        copyPings.setFocusable(false);
        screenshot = new JButton("Screenshot");
        screenshot.addActionListener(this);
        screenshot.setFocusable(false);
        theme = new JButton("Dark mode");
        theme.addActionListener(this);
        theme.setFocusable(false);

        // Build three groups: LEFT (FlowLayout.LEFT), CENTER (WrapLayout.CENTER), RIGHT (FlowLayout.RIGHT)
        leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        leftGroup.add(hostLabel);
        leftGroup.add(host);
        leftGroup.add(countLabel);
        leftGroup.add(count);
        leftGroup.add(startStop);
        leftGroup.add(clear);

        centerGroup = new JPanel(new WrapLayout(FlowLayout.CENTER, 6, 0));
        centerGroup.add(sentLabel);
        centerGroup.add(receivedLabel);
        centerGroup.add(lostLabel);
        centerGroup.add(lossLabel);
        centerGroup.add(bestLabel);
        centerGroup.add(averageLabel);
        centerGroup.add(medianLabel);
        centerGroup.add(worstLabel);
        centerGroup.add(lastLabel);
        centerGroup.add(elapsedLabel);

        rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        rightGroup.add(copyResults);
        rightGroup.add(copyPings);
        rightGroup.add(screenshot);
        rightGroup.add(theme);

        // Use BorderLayout so left/right keep preferred widths and center wraps as space changes
        controlsBar = new JPanel(new BorderLayout());

        // Center vertically when single row (keeps consistent look with left/right)
        centerWrapper = new JPanel(new GridBagLayout());
        GridBagConstraints centerGbc = new GridBagConstraints();
        centerGbc.anchor = GridBagConstraints.CENTER;
        centerGbc.weightx = 1.0;
        centerGbc.weighty = 1.0;
        centerWrapper.add(centerGroup, centerGbc);

        controlsBar.add(leftGroup, BorderLayout.WEST);
        controlsBar.add(centerWrapper, BorderLayout.CENTER);
        controlsBar.add(rightGroup, BorderLayout.EAST);

        // Ensure the center group recalculates its preferred size on resize so it can "flatten out" again
        controlsBar.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                centerGroup.revalidate();
                centerGroup.repaint();
            }
        });

        root = new JPanel(new BorderLayout());
        root.add(controlsBar, BorderLayout.NORTH);
        root.add(this, BorderLayout.CENTER);
        frame.setContentPane(root);

        reloadTheme();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }

    private void showInfoDialog(String message, String title) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, message, title, JOptionPane.INFORMATION_MESSAGE)
        );
    }

    private void showErrorDialog(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE)
        );
    }

    private int parseLatency(String line) {
        try {
            if (isWindows && line.contains("Zeit")) { // Windows de
                return Integer.parseInt(line.split("Zeit")[1].split("ms")[0].substring(1));
            }
            if (isWindows && line.contains("time")) { // Windows en
                return Integer.parseInt(line.split("time")[1].split("ms")[0].substring(1));
            }
            if (!isWindows && line.contains("time=")) { // Linux
                String msStr = line.split("time=")[1].split(" ")[0];
                return (int) Math.round(Double.parseDouble(msStr));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void updateLossLabels() {
        int lost = sent - received;
        lostLabel.setText("Lost: " + lost);
        double loss = (sent > 0) ? ((double) lost / sent * 100d) : 0d;
        lossLabel.setText("Loss: " + lossFormat.format(loss) + "%");
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

    private void resetStats() {
        pingTimestamps.clear();
        pingValues.clear();
        breakIndices.clear();
        lossIndices.clear();
        hoveredPingIndex = -1;
        sent = 0;
        received = 0;
        best = Integer.MAX_VALUE;
        average = 0;
        worst = -1;
        last = 0;
        runningSumOkPings = 0;
        medianLow.clear();
        medianHigh.clear();
        median = 0;
        elapsedTimer.stop();
        startStopTimestamps.clear();
        repaint();
    }

    private void copyResultsToClipboard() {
        try {
            String results = String.join("\t", new String[]{
                    sentLabel.getText(), receivedLabel.getText(), lostLabel.getText(), lossLabel.getText(),
                    bestLabel.getText(), averageLabel.getText(), medianLabel.getText(), worstLabel.getText(),
                    lastLabel.getText(), elapsedLabel.getText()
            });
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(results), null);
        } catch (Exception ex) {
            showErrorDialog("Failed to copy results to clipboard: " + ex.getMessage());
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
            for (int i = 0; i < pingTimestamps.size(); i++) {
                String value = lossIndices.contains(i) ? "Request timed out" : pingValues.get(i) + "ms";
                lines.add(timestampFormat.format(new Date(pingTimestamps.get(i))) + "\t" + value);
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(String.join("\n", lines)), null);
        } catch (Exception ex) {
            showErrorDialog("Failed to copy pings to clipboard: " + ex.getMessage());
        }
    }

    private void startPinging() {
        if (!lastHost.isEmpty() && !host.getText().equals(lastHost)) {
            clear.doClick();
        }
        lastHost = host.getText();
        startStopTimestamps.add(System.currentTimeMillis());
        elapsedTimer.start();
        startPingProcess();
    }

    private void stopPinging() {
        startStopTimestamps.add(System.currentTimeMillis());
        elapsedTimer.stop();
        updateElapsedLabel();
        breakIndices.add(pingTimestamps.size());
        stopPingProcess();
    }

    private void medianAdd(int value) {
        // Insert
        if (medianLow.isEmpty() || value <= medianLow.peek()) {
            medianLow.offer(value);
        } else {
            medianHigh.offer(value);
        }
        // Rebalance
        if (medianLow.size() > medianHigh.size() + 1) {
            medianHigh.offer(medianLow.poll());
        } else if (medianHigh.size() > medianLow.size()) {
            medianLow.offer(medianHigh.poll());
        }
        // Compute median
        if (medianLow.size() == medianHigh.size()) {
            median = (int) Math.round((medianLow.peek() + medianHigh.peek()) / 2.0);
        } else {
            median = medianLow.peek();
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
            boolean running = !startStopTimestamps.isEmpty();
            stopPingProcess();
            resetStats();
            resetLabels();
            if (running) {
                startPinging();
            }
        } else if (e.getSource().equals(copyResults)) {
            copyResultsToClipboard();
        } else if (e.getSource().equals(copyPings)) {
            copyPingsToClipboard();
        } else if (e.getSource().equals(screenshot)) {
            saveScreenshot();
        } else if (e.getSource().equals(theme)) {
            darkModeActive = !darkModeActive;
            prefs.putBoolean(PREF_DARK_MODE, darkModeActive);
            reloadTheme();
        }
    }

    private void startPingProcess() {
        try {
            int n = (int) count.getValue();

            ProcessBuilder pb;
            if (isWindows) {
                // Windows: -t for infinite, -n for count
                if (n == 0) {
                    pb = new ProcessBuilder("ping", "-t", host.getText());
                } else {
                    pb = new ProcessBuilder("ping", "-n", String.valueOf(n), host.getText());
                }
            } else {
                // Linux/Unix: continuous by default, -c for count
                if (n == 0) {
                    pb = new ProcessBuilder("ping", host.getText());
                } else {
                    pb = new ProcessBuilder("ping", "-c", String.valueOf(n), host.getText());
                }
            }
            pingProcess = pb.start();

            // Bump generation to invalidate any previous workers' UI updates
            runGeneration++;
            int gen = runGeneration;

            pingProcessInputStreamTask = new PingProcessInputStreamTask(pingProcess, gen);
            pingProcessInputStreamTask.execute();
            pingProcessErrorStreamTask = new PingProcessErrorStreamTask(pingProcess, gen);
            pingProcessErrorStreamTask.execute();
        } catch (Exception ex) {
            showErrorDialog("Failed to start ping process: " + ex.getMessage());
        }
    }

    private void stopPingProcess() {
        runGeneration++;
        if (pingProcess != null) {
            try {
                // Request graceful shutdown of the specific process
                pingProcess.destroy();

                // Proactively close streams to unblock SwingWorkers promptly
                try {
                    pingProcess.getInputStream().close();
                } catch (IOException ignored) {
                }
                try {
                    pingProcess.getErrorStream().close();
                } catch (IOException ignored) {
                }
                try {
                    pingProcess.getOutputStream().close();
                } catch (IOException ignored) {
                }

                // Asynchronously enforce after a short delay if still alive
                final Process procRef = pingProcess;
                new Thread(() -> {
                    try {
                        // Wait a bit for graceful shutdown
                        if (procRef != null) {
                            boolean exited = procRef.waitFor(2000, TimeUnit.MILLISECONDS);
                            if (!exited && procRef.isAlive()) {
                                procRef.destroyForcibly();
                            }
                        }
                    } catch (Exception ex) {
                        showErrorDialog("Failed to forcibly stop ping process: " + ex.getMessage());
                    }
                }, "PingStopEnforcer").start();
            } catch (Exception ex) {
                showErrorDialog("Failed to stop ping process: " + ex.getMessage());
            } finally {
                // Null out to avoid accidental reuse
                pingProcess = null;
            }
        }
        if (pingProcessInputStreamTask != null) {
            pingProcessInputStreamTask.cancel(true);
            pingProcessInputStreamTask = null;
        }
        if (pingProcessErrorStreamTask != null) {
            pingProcessErrorStreamTask.cancel(true);
            pingProcessErrorStreamTask = null;
        }
    }

    private void reloadTheme() {
        try {
            if (darkModeActive) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            FlatLaf.updateUI();

            refreshThemeColors();

            Color sep = (seperatorColor != null ? seperatorColor : (gridColor != null ? gridColor : withAlpha(Color.BLACK, 40)));
            controlsBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, sep));

            root.repaint();
            theme.setText(darkModeActive ? "Light mode" : "Dark mode");
        } catch (Exception ex) {
            showErrorDialog("Failed to apply theme: " + ex.getMessage());
        }
    }

    private void refreshThemeColors() {
        fgColor = UIManager.getColor("Label.foreground");
        gridColor = UIManager.getColor("Component.borderColor");
        seperatorColor = UIManager.getColor("Component.separatorColor");
        accentColor = new Color(63, 72, 204);
        dangerColor = UIManager.getColor("Actions.Red");
        if (dangerColor == null) dangerColor = Color.RED;

        // Derived colors
        axisColor = (fgColor != null ? fgColor : Color.BLACK);
        stoppedBandColor = withAlpha(axisColor, 28);
        Color baseDivider = (seperatorColor != null ? seperatorColor : (gridColor != null ? gridColor : axisColor));
        dividerColor = withAlpha(baseDivider, 220);
    }

    private String formatTime(long timeMs) {
        long hours = timeMs / 3_600_000L;
        long minutes = (timeMs % 3_600_000L) / 60_000L;
        long seconds = (timeMs % 60_000L) / 1000L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void updateElapsedLabel() {
        if (!startStopTimestamps.isEmpty()) {
            long elapsed = 0L;
            if (startStopTimestamps.size() > 1) {
                for (int i = 1; i < startStopTimestamps.size(); i += 2) {
                    elapsed += startStopTimestamps.get(i) - startStopTimestamps.get(i - 1);
                }
            }
            boolean running = startStopTimestamps.size() % 2 == 1;
            if (running) {
                elapsed += System.currentTimeMillis() - startStopTimestamps.get(startStopTimestamps.size() - 1);
            }
            elapsedLabel.setText("Elapsed: " + formatTime(elapsed));
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        int width = getWidth(), height = getHeight();
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Axis layout
        FontMetrics fm = g2d.getFontMetrics();
        int yLabelWidth = fm.stringWidth((worst <= 0 ? "0" : worst) + "ms");
        int xLabelHeight = fm.getHeight();
        int padding = 8;
        int leftMargin = Math.max(40, yLabelWidth + padding * 2);
        int bottomMargin = Math.max(28, xLabelHeight + padding * 2);
        int rightMargin = 12;
        int topMargin = 12;

        plotLeft = leftMargin;
        plotTop = topMargin;
        plotW = Math.max(1, width - leftMargin - rightMargin);
        plotH = Math.max(1, height - topMargin - bottomMargin);
        int plotBottom = plotTop + plotH;
        int plotRight = plotLeft + plotW;

        // Draw axes (left Y-axis, bottom X-axis)
        g2d.setColor(axisColor);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawLine(plotLeft, plotTop, plotLeft, plotBottom);
        g2d.drawLine(plotLeft, plotBottom, plotRight, plotBottom);

        // Y-axis labels
        String yBottom = "0ms";
        String yTop = (worst <= 0 ? "0" : worst) + "ms";
        // Top label
        int topLabelX = plotLeft - padding - fm.stringWidth(yTop);
        int topLabelY = plotTop + fm.getAscent();
        g2d.drawString(yTop, Math.max(2, topLabelX), Math.max(fm.getAscent(), topLabelY));
        // Bottom label
        int bottomLabelX = plotLeft - padding - fm.stringWidth(yBottom);
        int bottomLabelY = plotBottom - 2;
        g2d.drawString(yBottom, Math.max(2, bottomLabelX), bottomLabelY);

        // X-axis labels
        startTs = !startStopTimestamps.isEmpty() ? startStopTimestamps.get(0) : 0L;
        long lastTs = !pingTimestamps.isEmpty() ? pingTimestamps.get(pingTimestamps.size() - 1) : startTs;
        totalTime = lastTs - startTs;

        String xLeft = "0s";
        String xRight = formatTime(totalTime);
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

        final double xScale = totalTime > 0 ? (double) plotW / totalTime : 0.0;
        final double yScale = worst > 0 ? (double) plotH / worst : 0.0;

        // Draw section shading and vertical dividers
        if (!startStopTimestamps.isEmpty()) {
            g2d.setColor(stoppedBandColor);

            // Draw bands for stopped windows
            for (int i = 1; i < startStopTimestamps.size() - 1; i += 2) {
                long stopTs = startStopTimestamps.get(i);
                long nextStartTs = startStopTimestamps.get(i + 1);
                int xStart = plotLeft + (int) Math.round((stopTs - startTs) * xScale);
                int xEnd = plotLeft + (int) Math.round((nextStartTs - startTs) * xScale);
                xStart = Math.max(plotLeft, Math.min(plotRight, xStart));
                xEnd = Math.max(plotLeft, Math.min(plotRight, xEnd));
                if (xEnd > xStart) {
                    g2d.fillRect(xStart, plotTop, xEnd - xStart, plotH);
                }
            }

            // Dashed vertical divider lines at each press time
            g2d.setColor(dividerColor);
            g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, new float[]{6f, 6f}, 0f));

            for (int i = 1; i < startStopTimestamps.size(); i++) {
                if (startStopTimestamps.size() % 2 == 0 && i == startStopTimestamps.size() - 1) {
                    continue;
                }
                long ts = startStopTimestamps.get(i);
                int x = plotLeft + (int) Math.round((ts - startTs) * xScale);
                x = Math.max(plotLeft, Math.min(plotRight, x));
                g2d.drawLine(x, plotTop, x, plotBottom);
            }

            // Redraw axes to keep them crisp
            g2d.setColor(axisColor);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawLine(plotLeft, plotTop, plotLeft, plotBottom);
            g2d.drawLine(plotLeft, plotBottom, plotRight, plotBottom);
        }

        // Draw pings
        if (pingTimestamps.isEmpty()) {
            return;
        }
        // Use LOD if there are far more points than pixels
        int n = pingTimestamps.size();
        boolean useLOD = plotW > 0 && n > plotW * 2; // Threshold x2 pixels
        final int tickH = Math.max(4, Math.min(10, plotH / 30));

        if (useLOD) {
            // Arrays per on-screen x pixel
            int len = plotW + 1;
            int[] minVal = new int[len];
            int[] maxVal = new int[len];
            boolean[] hasData = new boolean[len];
            boolean[] hasTimeout = new boolean[len];

            Arrays.fill(minVal, Integer.MAX_VALUE);
            Arrays.fill(maxVal, Integer.MIN_VALUE);

            // Collect stats per pixel
            for (int i = 0; i < n; i++) {
                long ts = pingTimestamps.get(i);
                int val = pingValues.get(i);

                int idx = (int) Math.round((ts - startTs) * xScale);
                idx = Math.max(0, Math.min(plotW, idx));

                if (lossIndices.contains(i)) {
                    hasTimeout[idx] = true;
                } else {
                    hasData[idx] = true;
                    if (val < minVal[idx]) {
                        minVal[idx] = val;
                    }
                    if (val > maxVal[idx]) {
                        maxVal[idx] = val;
                    }
                }
            }

            // Draw envelope (vertical segments per pixel) and centerline
            Path2D centerLinePath = new Path2D.Double();
            boolean drawing = false;

            g2d.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
            for (int i = 0; i < len; i++) {
                int x = plotLeft + i;

                if (hasData[i]) {
                    int y1 = plotBottom - (int) Math.round(minVal[i] * yScale);
                    int y2 = plotBottom - (int) Math.round(maxVal[i] * yScale);

                    y1 = Math.max(plotTop, Math.min(plotBottom, y1));
                    y2 = Math.max(plotTop, Math.min(plotBottom, y2));

                    g2d.setColor(accentColor);
                    g2d.drawLine(x, y1, x, y2);

                    // Centerline
                    double mid = 0.5 * (minVal[i] + maxVal[i]);
                    int yMid = plotBottom - (int) Math.round(mid * yScale);
                    if (!drawing) {
                        centerLinePath.moveTo(x, yMid);
                        drawing = true;
                    } else {
                        centerLinePath.lineTo(x, yMid);
                    }
                } else {
                    // Gap in data: break the centerline
                    drawing = false;
                }

                if (hasTimeout[i]) {
                    g2d.setColor(dangerColor);
                    g2d.setStroke(new BasicStroke(2f));
                    g2d.drawLine(x, plotBottom, x, plotBottom - tickH);
                }
            }

            g2d.setColor(accentColor);
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.draw(centerLinePath);
        } else {
            // Exact Path2D polyline (no decimation)
            Path2D pingPath = new Path2D.Double();
            boolean drawing = false; // Whether we are currently inside a run of non-timeout points
            int runLen = 0;          // Length of the current run (to detect singletons)
            int lastX = 0, lastY = 0;

            int r = 3;
            for (int i = 0; i < n; i++) {
                long ts = pingTimestamps.get(i);
                int val = pingValues.get(i);

                int x = plotLeft + (int) Math.round((ts - startTs) * xScale);
                x = Math.max(plotLeft, Math.min(plotRight, x));
                int y = plotBottom - (int) Math.round(val * yScale);
                y = Math.max(plotTop, Math.min(plotBottom, y));

                boolean timeout = lossIndices.contains(i);
                boolean breakHere = breakIndices.contains(i); // Boundary before current point

                // If a break or timeout occurs, close any ongoing run first
                if (timeout || breakHere) {
                    if (drawing) {
                        // If the run had only a single point, draw it as a dot
                        if (runLen == 1) {
                            g2d.setColor(accentColor);
                            g2d.fillOval(lastX - r, lastY - r, r * 2, r * 2);
                        }
                        drawing = false;
                        runLen = 0;
                    }
                    // Draw timeout tick immediately
                    if (timeout) {
                        g2d.setColor(dangerColor);
                        g2d.setStroke(new BasicStroke(2f));
                        g2d.drawLine(x, plotBottom, x, plotBottom - tickH);
                        continue; // Skip adding this point to the path
                    }
                    // If it was a break (not timeout), we still need to start a fresh segment below
                }

                // Normal (non-timeout) point: extend or start the path
                if (!drawing) {
                    pingPath.moveTo(x, y);
                    drawing = true;
                    runLen = 1;
                } else {
                    pingPath.lineTo(x, y);
                    runLen++;
                }
                lastX = x;
                lastY = y;
            }

            // Finalize: if the last run was a singleton, draw its dot
            if (drawing && runLen == 1) {
                g2d.setColor(accentColor);
                g2d.fillOval(lastX - r, lastY - r, r * 2, r * 2);
            }

            g2d.setColor(accentColor);
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.draw(pingPath);
        }

        // Draw tooltip using hovered index
        if (hoveredPingIndex != -1) {
            long ts = pingTimestamps.get(hoveredPingIndex);
            int val = pingValues.get(hoveredPingIndex);
            int px = plotLeft + (int) Math.round((ts - startTs) * xScale);
            int py = plotBottom - (int) Math.round(val * yScale);
            px = Math.min(plotRight, px);
            py = Math.max(plotTop, py);

            g2d.setColor(axisColor);
            g2d.drawOval(px - 5, py - 5, 10, 10);
            g2d.setFont(g2d.getFont().deriveFont(Font.BOLD));
            fm = g2d.getFontMetrics();
            String s = timestampFormat.format(new Date(ts)) + " " + (lossIndices.contains(hoveredPingIndex) ? "Request timed out" : val + "ms");
            int tx = px + 6;
            int ty = py - 6;

            if (tx > width - fm.stringWidth(s)) {
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

    private static Color withAlpha(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private void saveScreenshot() {
        try {
            // Create image and paint the entire frame (content) into it
            BufferedImage img = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            frame.paintAll(g2d);
            g2d.dispose();

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
                File file = chooser.getSelectedFile();
                // Ensure .png extension
                if (!file.getName().toLowerCase().endsWith(".png")) {
                    file = new File(file.getParentFile(), file.getName() + ".png");
                }
                ImageIO.write(img, "png", file);

                // Also copy the screenshot to the clipboard
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(img), null);

                showInfoDialog("Screenshot saved:\n" + file.getAbsolutePath(), "Screenshot");
            }
        } catch (Exception ex) {
            showErrorDialog("Failed to save screenshot: " + ex.getMessage());
        }
    }

    private class PingProcessInputStreamTask extends SwingWorker<Void, String> {
        private final Process procRef;
        private final int generation;

        private PingProcessInputStreamTask(Process procRef, int generation) {
            this.procRef = procRef;
            this.generation = generation;
        }

        @Override
        protected Void doInBackground() {
            try (Scanner s = new Scanner(procRef.getInputStream(), Charset.defaultCharset().name())) {
                while (!isCancelled() && s.hasNext()) {
                    String input = s.nextLine();
                    publish(input);
                }
            }
            return null;
        }

        @Override
        protected void process(List<String> inputChunks) {
            if (generation != runGeneration) return;
            for (String input : inputChunks) {
                // Windows success lines ("Antwort", "Reply") and Linux/Unix success lines (contains "time=")
                if (input.startsWith("Antwort") || input.startsWith("Reply") || input.contains("time=")) {
                    sent++;
                    sentLabel.setText("Sent: " + sent);
                    received++;
                    receivedLabel.setText("Received: " + received);
                    updateLossLabels();

                    last = parseLatency(input);

                    pingTimestamps.add(System.currentTimeMillis());
                    pingValues.add(last);

                    if (last < best) {
                        best = last;
                        bestLabel.setText("Best: " + best + "ms");
                    }
                    if (last > worst) {
                        worst = last;
                        worstLabel.setText("Worst: " + worst + "ms");
                    }

                    runningSumOkPings += last;
                    average = (int) Math.round((double) runningSumOkPings / received);
                    averageLabel.setText("Average: " + average + "ms");

                    medianAdd(last);
                    medianLabel.setText("Median: " + median + "ms");

                    lastLabel.setText("Last: " + last + "ms");
                    repaint();
                } else if (
                        input.startsWith("Zeitüberschreitung")
                                || input.startsWith("PING: Fehler")
                                || input.startsWith("Request timed out")
                                || input.startsWith("PING: transmit failed")
                                || input.toLowerCase().contains("timeout")
                                || input.toLowerCase().contains("unreachable")
                                || input.toLowerCase().contains("time to live exceeded")
                ) {
                    sent++;
                    sentLabel.setText("Sent: " + sent);
                    lossIndices.add(pingTimestamps.size());
                    updateLossLabels();
                    pingTimestamps.add(System.currentTimeMillis());
                    pingValues.add(0);
                    repaint();
                } else if (input.startsWith("Ping-Anforderung") || input.startsWith("Ping request") || input.contains("Name or service not known")) {
                    showErrorDialog(input);
                }
            }
        }

        @Override
        protected void done() {
            if (generation != runGeneration) return;
            startStopTimestamps.add(System.currentTimeMillis());
            elapsedTimer.stop();
            updateElapsedLabel();

            breakIndices.add(pingTimestamps.size());
            startStop.setText("Start");
        }
    }

    private class PingProcessErrorStreamTask extends SwingWorker<Void, String> {
        private final ArrayList<String> errorLines = new ArrayList<>();
        private final Process procRef;
        private final int generation;

        private PingProcessErrorStreamTask(Process procRef, int generation) {
            this.procRef = procRef;
            this.generation = generation;
        }

        @Override
        protected Void doInBackground() {
            try (Scanner s = new Scanner(procRef.getErrorStream(), Charset.defaultCharset().name())) {
                while (!isCancelled() && s.hasNext()) {
                    errorLines.add(s.nextLine());
                }
            }
            return null;
        }

        @Override
        protected void done() {
            if (generation != runGeneration) return;
            if (!errorLines.isEmpty()) {
                showErrorDialog(String.join("\n", errorLines));
            }
        }
    }
}
