package de.matthesvoss.pingtest.gui;

import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import de.matthesvoss.pingtest.controller.PingController;
import de.matthesvoss.pingtest.gui.theme.ThemeManager;
import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.model.PingStatistics;
import de.matthesvoss.pingtest.model.PreferencesManager;
import de.matthesvoss.pingtest.resources.ResourceLoader;
import de.matthesvoss.pingtest.service.PingProcessListener;
import de.matthesvoss.pingtest.service.exceptions.PingProcessException;
import de.matthesvoss.pingtest.service.exceptions.UnknownHostException;
import de.matthesvoss.pingtest.util.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class PingPanel extends JPanel implements ActionListener, PingProcessListener {
    private static final DecimalFormat LOSS_FORMAT = new DecimalFormat("0.00");
    private final PingController pingController;
    private final PingStatistics statistics = new PingStatistics(new MedianCalculator());
    private final Timer elapsedTimer = new Timer(1000, e -> updateElapsedLabel());
    private final PreferencesManager prefs;
    private JFrame frame;
    private PingChart chart;
    private JLabel sentLabel, receivedLabel, lostLabel, lossLabel, bestLabel, averageLabel, medianLabel, worstLabel,
            lastLabel, elapsedLabel;
    private JTextField host;
    private JButton startStop, clear, share, theme;
    private JPopupMenu shareMenu;
    private JMenuItem copyStats, copyPings, copyScreenshot, saveScreenshot;
    private JSpinner count;
    private JCheckBox infinite;
    private MessageListener messageListener;
    private String lastHost = "";
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
        frame = new JFrame("Ping Test");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setIconImages(ResourceLoader.loadFrameIcons());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClosing();
            }
        });

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(createControlsPanel());
        topPanel.add(createStatsPanel());
        topPanel.add(new JSeparator(SwingConstants.HORIZONTAL));

        JPanel root = new JPanel(new BorderLayout());
        root.add(topPanel, BorderLayout.NORTH);
        chart = new PingChart(statistics);
        root.add(chart, BorderLayout.CENTER);
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
        elapsedLabel.setText("Elapsed: " + Formatter.formatTimeInterval(statistics.getElapsedTime()));
    }

    private void resetStats() {
        statistics.reset();
        elapsedTimer.stop();
        chart.reset();
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
            Clipboard.copyToClipboard(stats);
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
                lines.add(Formatter.formatTimestampMs(ping.getTimestamp()) + "\t" + value);
            }
            Clipboard.copyToClipboard(String.join("\n", lines));
        } catch (Exception ex) {
            messageListener.onMessage("Failed to copy pings to clipboard", MessageType.ERROR, ex);
        }
    }

    private void copyScreenshotToClipboard() {
        Clipboard.copyToClipboard(frame);
    }

    private void saveScreenshot() {
        BufferedImage img = Screenshot.takeScreenshot(frame);

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
                Screenshot.saveScreenshot(img, file);
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
        chart.repaint();
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
            messageListener.onMessage("Unknown host: " + host.getText(), MessageType.ERROR, null);
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
}
