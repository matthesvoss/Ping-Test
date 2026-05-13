package de.matthesvoss.pingtest.gui.theme;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public final class ThemeColors {
    private static Color accentColor;
    private static Color dangerColor;
    private static Color foregroundColor;
    private static Color stoppedBandColor;
    private static Color dividerColor;
    private static Color borderColor;
    private static Color separatorColor;

    private ThemeColors() {
    }

    public static void refresh() {
        foregroundColor = Optional.ofNullable(UIManager.getColor("Label.foreground"))
                .orElse(ThemeManager.isDarkTheme() ? Color.WHITE : Color.BLACK);
        accentColor = Optional.ofNullable(UIManager.getColor("Component.accentColor"))
                .orElse(Color.BLUE);
        dangerColor = Optional.ofNullable(UIManager.getColor("Actions.Red"))
                .orElse(Color.RED);
        stoppedBandColor = withAlpha(foregroundColor, 28);
        dividerColor = withAlpha(foregroundColor, 170);
        borderColor = Optional.ofNullable(UIManager.getColor("Component.borderColor"))
                .orElse(Color.LIGHT_GRAY);
        separatorColor = Optional.ofNullable(UIManager.getColor("Separator.foreground"))
                .orElse(Color.GRAY);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Color foreground() {
        return foregroundColor;
    }

    public static Color accent() {
        return accentColor;
    }

    public static Color danger() {
        return dangerColor;
    }

    public static Color stoppedBand() {
        return stoppedBandColor;
    }

    public static Color divider() {
        return dividerColor;
    }

    public static Color border() {
        return borderColor;
    }

    public static Color separator() {
        return separatorColor;
    }
}
