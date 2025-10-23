package de.matthesvoss.pingtest.service.exceptions;

public class UnknownHostException extends PingProcessException {
    public UnknownHostException(String message) {
        super(message);
    }
}
