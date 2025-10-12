package de.matthesvoss.pingtest.model;

import java.util.prefs.Preferences;

public class PreferencesManager {
    private static final String KEY_DARK_MODE = "darkModeActive";
    private static final String KEY_LAST_HOST = "lastHost";
    private static final String KEY_WINDOW_X = "windowX";
    private static final String KEY_WINDOW_Y = "windowY";
    private static final String KEY_WINDOW_W = "windowW";
    private static final String KEY_WINDOW_H = "windowH";
    private static final String KEY_WINDOW_EXTENDED_STATE = "windowExtendedState";

    private final Preferences prefs;

    public PreferencesManager(Class<?> prefsNodeClass) {
        this.prefs = Preferences.userNodeForPackage(prefsNodeClass);
    }

    public boolean isDarkModeActive(boolean defaultValue) {
        return prefs.getBoolean(KEY_DARK_MODE, defaultValue);
    }

    public void setDarkModeActive(boolean active) {
        prefs.putBoolean(KEY_DARK_MODE, active);
    }

    public String getLastHost(String defaultValue) {
        return prefs.get(KEY_LAST_HOST, defaultValue);
    }

    public void setLastHost(String host) {
        if (host == null) {
            prefs.remove(KEY_LAST_HOST);
        } else {
            prefs.put(KEY_LAST_HOST, host);
        }
    }

    public boolean hasWindowBounds() {
        return prefs.getInt(KEY_WINDOW_W, -1) != -1;
    }

    public void putWindowBounds(int x, int y, int w, int h, int extendedState) {
        prefs.putInt(KEY_WINDOW_X, x);
        prefs.putInt(KEY_WINDOW_Y, y);
        prefs.putInt(KEY_WINDOW_W, w);
        prefs.putInt(KEY_WINDOW_H, h);
        prefs.putInt(KEY_WINDOW_EXTENDED_STATE, extendedState);
    }

    public int getWindowX(int defaultValue) {
        return prefs.getInt(KEY_WINDOW_X, defaultValue);
    }

    public int getWindowY(int defaultValue) {
        return prefs.getInt(KEY_WINDOW_Y, defaultValue);
    }

    public int getWindowW(int defaultValue) {
        return prefs.getInt(KEY_WINDOW_W, defaultValue);
    }

    public int getWindowH(int defaultValue) {
        return prefs.getInt(KEY_WINDOW_H, defaultValue);
    }

    public int getWindowExtendedState(int defaultValue) {
        return prefs.getInt(KEY_WINDOW_EXTENDED_STATE, defaultValue);
    }
}
