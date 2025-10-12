package de.matthesvoss.pingtest.service;

import de.matthesvoss.pingtest.model.PingResult;

public interface PingProcessListener {
    void onPing(PingResult ping);

    default void onProcessFinished() {}
}
