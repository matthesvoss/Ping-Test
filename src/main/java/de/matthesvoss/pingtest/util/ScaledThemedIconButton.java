package de.matthesvoss.pingtest.util;

import javax.swing.*;

public class ScaledThemedIconButton extends JButton {
    private final ScaledThemedIconSupport support;

    public ScaledThemedIconButton(String text, String themedIconName, double heightFactor) {
        super(text);
        support = new ScaledThemedIconSupport(this, themedIconName, heightFactor);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (support != null) {
            support.updateIcon();
        }
    }
}
