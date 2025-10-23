package de.matthesvoss.pingtest.service.exceptions;

public class PingProcessException extends Exception {
    public PingProcessException(String message) {
        super(message);
    }

    public PingProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
