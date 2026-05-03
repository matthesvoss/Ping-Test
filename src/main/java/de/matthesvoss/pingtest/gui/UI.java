package de.matthesvoss.pingtest.gui;

import de.matthesvoss.pingtest.util.ScaledThemedIconButton;

import javax.swing.*;
import java.awt.*;

final class UI {
    private UI() {
    }

    static JPanel panel(LayoutManager layout, Component... components) {
        JPanel p = new JPanel(layout);
        for (Component c : components) {
            p.add(c);
        }
        return p;
    }

    static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        if (action != null) {
            b.addActionListener(e -> action.run());
        }
        b.setFocusPainted(false);
        return b;
    }

    static ScaledThemedIconButton iconButton(String text, String iconName, double heightFactor,
                                             Runnable action) {
        ScaledThemedIconButton b = new ScaledThemedIconButton(text, iconName, heightFactor);
        if (action != null) {
            b.addActionListener(e -> action.run());
        }
        b.setFocusPainted(false);
        return b;
    }

    static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem m = new JMenuItem(text);
        if (action != null) {
            m.addActionListener(e -> action.run());
        }
        return m;
    }
}
