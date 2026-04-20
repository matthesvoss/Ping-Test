package de.matthesvoss.pingtest.util;

import de.matthesvoss.pingtest.resources.ResourceLoader;

import javax.swing.*;
import java.awt.image.BufferedImage;

class ScaledThemedIconSupport {
    private final AbstractButton button;
    private final String themedIconName;
    private final double heightFactor;

    ScaledThemedIconSupport(AbstractButton button, String themedIconName, double heightFactor) {
        this.button = button;
        this.themedIconName = themedIconName;
        this.heightFactor = heightFactor;
        initButton();
        updateIcon();
    }

    private void initButton() {
        button.setFocusPainted(false);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(6);
    }

    void updateIcon() {
        if (themedIconName == null) {
            return;
        }
        BufferedImage icon = ResourceLoader.loadThemedIcon(themedIconName);
        button.setIcon(new ScaledIcon(icon, heightFactor, button));
    }
}
