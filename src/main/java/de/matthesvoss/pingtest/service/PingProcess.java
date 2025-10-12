package de.matthesvoss.pingtest.service;

import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.util.MessageListener;
import de.matthesvoss.pingtest.util.MessageType;
import de.matthesvoss.pingtest.util.Utils;

import javax.swing.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class PingProcess {
    private final String host;
    private final MessageListener messageListener;
    private final PingProcessListener pingListener;
    private Process pingProcess;
    private InputStreamWorker inputStreamWorker;
    private Thread errorStreamReader;
    private volatile boolean running;

    public PingProcess(String host, MessageListener messageListener, PingProcessListener pingListener) {
        this.host = host;
        this.messageListener = messageListener;
        this.pingListener = pingListener;
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
            messageListener.onMessage("Failed to start ping process", MessageType.ERROR, ex);
        }
    }

    private ProcessBuilder getProcessBuilder(int count) {
        if (Utils.IS_WINDOWS) {
            // Windows: -t for infinite, -n for count
            if (count == 0) {
                return new ProcessBuilder("ping", "-t", host);
            } else {
                return new ProcessBuilder("ping", "-n", String.valueOf(count), host);
            }
        } else {
            // Linux/Unix: continuous by default, -c for count
            if (count == 0) {
                return new ProcessBuilder("ping", host);
            } else {
                return new ProcessBuilder("ping", "-c", String.valueOf(count), host);
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
            try {
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
                    } catch (Exception ex) {
                        messageListener.onMessage("Failed to forcibly stop ping process", MessageType.ERROR, ex);
                    }
                }, "PingProcessStopEnforcer").start();
            } catch (Exception ex) {
                messageListener.onMessage("Failed to stop ping process", MessageType.ERROR, ex);
            }
        }
    }

    private boolean isAlive() {
        return pingProcess != null && pingProcess.isAlive();
    }

    public boolean isRunning() {
        return running;
    }

    private class InputStreamWorker extends SwingWorker<Void, PingResult> {
        private final PingParser parser = new PingParser(messageListener);

        @Override
        protected Void doInBackground() {
            try (Scanner s = new Scanner(pingProcess.getInputStream(), Charset.defaultCharset().name())) {
                while (!isCancelled() && s.hasNext()) {
                    String line = s.nextLine();
                    PingResult ping = parser.parseLine(line);
                    if (ping != null) {
                        publish(ping);
                    }
                }
            } catch (Exception ex) {
                messageListener.onMessage("Error reading ping process input stream", MessageType.ERROR, ex);
            }
            return null;
        }

        @Override
        protected void process(List<PingResult> chunks) {
            if (!running) {
                return;
            }
            for (PingResult ping : chunks) {
                pingListener.onPing(ping);
            }
        }

        @Override
        protected void done() {
            if (!running){
                return;
            }
            running = false;
            pingListener.onProcessFinished();
            if (errorStreamReader != null && errorStreamReader.isAlive()) {
                errorStreamReader.interrupt();
            }
        }
    }

    private Thread createAndStartErrorStreamReader() {
        Thread t = new Thread(() -> {
            try (Scanner s = new Scanner(pingProcess.getErrorStream(), Charset.defaultCharset().name())) {
                while (!Thread.currentThread().isInterrupted() && s.hasNextLine()) {
                    String line = s.nextLine();
                    messageListener.onMessage(line, MessageType.ERROR);
                }
            } catch (Exception ex) {
                messageListener.onMessage("Error reading ping process error stream", MessageType.ERROR, ex);
            }
        }, "PingProcessErrorStreamReader");

        t.setDaemon(true);
        t.start();
        return t;
    }
}
