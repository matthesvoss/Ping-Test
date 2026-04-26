package de.matthesvoss.pingtest.util;

@FunctionalInterface
public interface MessageListener {
    void onMessage(String message, MessageType type, Throwable cause);
}
