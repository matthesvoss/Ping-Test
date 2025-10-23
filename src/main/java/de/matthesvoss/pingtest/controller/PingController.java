package de.matthesvoss.pingtest.controller;

import de.matthesvoss.pingtest.service.PingProcess;
import de.matthesvoss.pingtest.service.PingProcessListener;

public class PingController {
    private PingProcess process;

    public void startPinging(String host, int count, PingProcessListener processListener) {
        stopPinging();
        process = new PingProcess(host, processListener);
        process.start(count);
    }

    public void stopPinging() {
        if (process != null && process.isRunning()) {
            process.stop();
        }
    }
}
