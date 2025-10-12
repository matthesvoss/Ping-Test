package de.matthesvoss.pingtest.controller;

import de.matthesvoss.pingtest.service.PingProcess;
import de.matthesvoss.pingtest.service.PingProcessListener;
import de.matthesvoss.pingtest.util.MessageListener;

public class PingController {
    private PingProcess process;
    private MessageListener messageListener;

    public void startPinging(String host, int count, PingProcessListener pingListener) {
        stopPinging();
        process = new PingProcess(host, messageListener, pingListener);
        process.start(count);
    }

    public void stopPinging() {
        if (process != null && process.isRunning()) {
            process.stop();
        }
    }

    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }
}
