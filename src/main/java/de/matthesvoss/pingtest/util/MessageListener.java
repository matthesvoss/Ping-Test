package de.matthesvoss.pingtest.util;

@FunctionalInterface
public interface MessageListener {
    void onMessage(String message, MessageType type, Throwable throwable);

    default void onMessage(String message, MessageType type) {
        onMessage(message, type, null);
    }
}
