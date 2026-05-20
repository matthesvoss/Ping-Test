package de.matthesvoss.pingtest.service;

import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.service.exceptions.PingProcessException;

public interface PingProcessListener {
    void onPing(PingResult ping);

    void onIPAddress(String address);

    void onProcessException(PingProcessException ex);

    default void onProcessFinished() {
    }
}
