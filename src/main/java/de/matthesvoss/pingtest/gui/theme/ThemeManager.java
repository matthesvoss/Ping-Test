package de.matthesvoss.pingtest.gui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

public final class ThemeManager {
    private static boolean darkTheme;

    private ThemeManager() {
    }

    public static void init(boolean dark) {
        FlatLaf.registerCustomDefaultsSource("de.matthesvoss.pingtest.themes");
        apply(dark);
    }

    public static void switchTheme() {
        apply(!darkTheme);
    }

    public static boolean isDarkTheme() {
        return darkTheme;
    }

    public static void apply(boolean dark) {
        darkTheme = dark;

        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        FlatLaf.updateUI();
        ThemeColors.refresh();
    }
}
