package de.matthesvoss.pingtest.gui.theme;

import javax.swing.*;
import java.awt.*;

public class ThemeColors {
    private static Color accentColor;
    private static Color dangerColor;
    private static Color axisColor;
    private static Color stoppedBandColor;
    private static Color dividerColor;
    private static Color separatorColor;

    private ThemeColors() {}

    public static void refresh() {
        Color fgColor = UIManager.getColor("Label.foreground");
        Color borderColor = UIManager.getColor("Component.borderColor");
        accentColor = UIManager.getColor("Component.accentColor");
        dangerColor = UIManager.getColor("Actions.Red");
        if (dangerColor == null) {
            dangerColor = Color.RED;
        }

        // Derived colors
        axisColor = (fgColor != null ? fgColor : Color.BLACK);
        stoppedBandColor = withAlpha(axisColor, 28);
        Color baseDivider = (separatorColor != null ? separatorColor : (borderColor != null ? borderColor : axisColor));
        dividerColor = withAlpha(baseDivider, 220);
        separatorColor = borderColor != null ? borderColor : withAlpha(Color.BLACK, 40);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Color accent() {
        return accentColor;
    }

    public static Color danger() {
        return dangerColor;
    }

    public static Color axis() {
        return axisColor;
    }

    public static Color stoppedBand() {
        return stoppedBandColor;
    }

    public static Color divider() {
        return dividerColor;
    }

    public static Color separator() {
        return separatorColor;
    }
}
