package de.matthesvoss.pingtest.gui.theme;

public class ThemeManager {
    private static boolean darkTheme;

    private ThemeManager() {}

    public static void switchTheme() {
        setDarkTheme(!darkTheme);
    }

    public static void setDarkTheme(boolean dark) {
        darkTheme = dark;
    }

    public static boolean isDarkTheme() {
        return darkTheme;
    }
}
