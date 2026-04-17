package de.matthesvoss.pingtest.util;

import de.matthesvoss.pingtest.resources.ResourceLoader;

import javax.swing.*;
import java.awt.image.BufferedImage;

public class ScaledThemedIconButton extends JButton {
    private final String themedIconName;
    private final double heightFactor;

    public ScaledThemedIconButton(String text, String themedIconName, double heightFactor) {
        super(text);
        this.themedIconName = themedIconName;
        this.heightFactor = heightFactor;
        initButton();
        updateIcon();
    }

    private void initButton() {
        setFocusPainted(false);
        setHorizontalTextPosition(SwingConstants.RIGHT);
        setIconTextGap(6);
    }

    private void updateIcon() {
        if (themedIconName == null) {
            return;
        }
        BufferedImage icon = ResourceLoader.loadThemedIcon(themedIconName);
        setIcon(new ScaledIcon(icon, heightFactor, this));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        updateIcon();
    }
}
