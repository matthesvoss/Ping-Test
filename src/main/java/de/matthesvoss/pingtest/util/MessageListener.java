package de.matthesvoss.pingtest.util;

@FunctionalInterface
public interface MessageListener {
    void onMessage(String message, MessageType type, Exception e);

    default void onMessage(String message, MessageType type) {
        onMessage(message, type, null);
    }
}
