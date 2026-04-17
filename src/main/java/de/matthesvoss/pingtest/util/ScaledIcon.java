package de.matthesvoss.pingtest.util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ScaledIcon implements Icon {
    private final BufferedImage icon;
    private final double heightFactor; // fraction of component font height
    private int targetWidth, targetHeight;

    public ScaledIcon(BufferedImage icon, double heightFactor, JComponent parent) {
        this.icon = icon;
        this.heightFactor = heightFactor;
        updateScaledSize(parent);
    }

    private void updateScaledSize(Component c) {
        if (icon == null || c == null || c.getFont() == null) {
            targetWidth = targetHeight = 0;
            return;
        }

        int fontHeight = c.getFontMetrics(c.getFont()).getHeight();
        targetHeight = (int) (fontHeight * heightFactor);
        double scale = (double) targetHeight / icon.getHeight();
        targetWidth = (int) (icon.getWidth() * scale);
    }

    /**
     * Draw the icon scaled to the component's current font height
     */
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (icon == null) {
            return;
        }
        updateScaledSize(c);

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g2d.drawImage(icon, x, y, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return targetWidth;
    }

    @Override
    public int getIconHeight() {
        return targetHeight;
    }
}
