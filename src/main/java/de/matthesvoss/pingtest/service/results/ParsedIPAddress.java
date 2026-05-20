package de.matthesvoss.pingtest.service.results;

import de.matthesvoss.pingtest.service.PingProcessListener;

public class ParsedIPAddress implements ParsedLine {
    private final String address;

    public ParsedIPAddress(String address) {
        this.address = address;
    }

    @Override
    public void dispatch(PingProcessListener listener) {
        listener.onIPAddress(address);
    }
}
