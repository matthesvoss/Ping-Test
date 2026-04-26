package de.matthesvoss.pingtest.service;

import de.matthesvoss.pingtest.Application;
import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.service.exceptions.PingProcessException;
import de.matthesvoss.pingtest.service.exceptions.UnknownHostException;

import javax.swing.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class PingProcess {
    private final String host;
    private final PingProcessListener processListener;
    private final PingParser parser = new PingParser();
    private Process pingProcess;
    private InputStreamWorker inputStreamWorker;
    private Thread errorStreamReader;
    private volatile boolean running;

    public PingProcess(String host, PingProcessListener processListener) {
        this.host = host;
        this.processListener = processListener;
    }

    public synchronized void start(int count) {
        if (isAlive()) {
            return;
        }
        try {
            ProcessBuilder pb = getProcessBuilder(count);
            pingProcess = pb.start();
            running = true;

            inputStreamWorker = new InputStreamWorker();
            inputStreamWorker.execute();

            errorStreamReader = createAndStartErrorStreamReader();
        } catch (Exception ex) {
            processListener.onProcessException(new PingProcessException("Failed to start ping process", ex));
        }
    }

    private ProcessBuilder getProcessBuilder(int count) {
        if (Application.IS_WINDOWS) {
            // Windows: -t for infinite, -n for count
            if (count == -1) {
                return new ProcessBuilder("ping", "-w", "1000", "-t", host);
            } else {
                return new ProcessBuilder("ping", "-w", "1000", "-n", String.valueOf(count), host);
            }
        } else {
            // Linux: continuous by default, -c for count
            if (count == -1) {
                return new ProcessBuilder("ping", "-O", "-n", "-i", "1", "-W", "1", host);
            } else {
                return new ProcessBuilder("ping", "-O", "-n", "-i", "1", "-W", "1", "-c", String.valueOf(count), host);
            }
        }
    }

    public synchronized void stop() {
        running = false;

        if (inputStreamWorker != null) {
            inputStreamWorker.cancel(true);
            inputStreamWorker = null;
        }

        if (errorStreamReader != null && errorStreamReader.isAlive()) {
            errorStreamReader.interrupt();
        }

        if (isAlive()) {
            // Request graceful shutdown of the specific process
            pingProcess.destroy();

            // Asynchronously enforce after a short delay if still alive
            new Thread(() -> {
                try {
                    if (isAlive()) {
                        // Wait a bit for graceful shutdown
                        boolean exited = pingProcess.waitFor(2000, TimeUnit.MILLISECONDS);
                        if (!exited && pingProcess.isAlive()) {
                            pingProcess.destroyForcibly();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }, "PingProcessStopEnforcer").start();
        }
    }

    private boolean isAlive() {
        return pingProcess != null && pingProcess.isAlive();
    }

    public boolean isRunning() {
        return running;
    }

    private Thread createAndStartErrorStreamReader() {
        Thread t = new Thread(() -> {
            try (Scanner s = new Scanner(pingProcess.getErrorStream(), Charset.defaultCharset().name())) {
                while (!Thread.currentThread().isInterrupted() && s.hasNextLine()) {
                    String line = s.nextLine();
                    try {
                        parser.parseErrorLine(line);
                    } catch (PingProcessException ex) {
                        processListener.onProcessException(ex);
                    }
                }
            } catch (Exception ex) {
                processListener.onProcessException(
                        new PingProcessException("Error reading ping process error stream", ex));
            }
        }, "PingProcessErrorStreamReader");

        t.setDaemon(true);
        t.start();
        return t;
    }

    private class InputStreamWorker extends SwingWorker<Void, PingResult> {
        @Override
        protected Void doInBackground() {
            try (Scanner s = new Scanner(pingProcess.getInputStream(), Charset.defaultCharset().name())) {
                while (!isCancelled() && s.hasNext()) {
                    String line = s.nextLine();
                    PingResult ping = parser.parseInputLine(line);
                    if (ping != null) {
                        publish(ping);
                    }
                }
            } catch (UnknownHostException ex) {
                processListener.onProcessException(ex);
            } catch (Exception ex) {
                processListener.onProcessException(
                        new PingProcessException("Error reading ping process input stream", ex));
            }
            return null;
        }

        @Override
        protected void process(List<PingResult> chunks) {
            if (!running) {
                return;
            }
            for (PingResult ping : chunks) {
                processListener.onPing(ping);
            }
        }

        @Override
        protected void done() {
            if (!running) {
                return;
            }
            running = false;
            processListener.onProcessFinished();
            if (errorStreamReader != null && errorStreamReader.isAlive()) {
                errorStreamReader.interrupt();
            }
        }
    }
}
