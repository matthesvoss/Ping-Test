package de.matthesvoss.pingtest.service.results;

import de.matthesvoss.pingtest.service.PingProcessListener;

public interface ParsedLine {
    void dispatch(PingProcessListener listener);
}
