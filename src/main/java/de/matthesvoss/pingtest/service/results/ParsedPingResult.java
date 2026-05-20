package de.matthesvoss.pingtest.service.results;

import de.matthesvoss.pingtest.model.PingResult;
import de.matthesvoss.pingtest.service.PingProcessListener;

public class ParsedPingResult implements ParsedLine {
    private final PingResult result;

    public ParsedPingResult(PingResult result) {
        this.result = result;
    }

    @Override
    public void dispatch(PingProcessListener listener) {
        listener.onPing(result);
    }
}
