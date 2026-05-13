package de.matthesvoss.pingtest.gui;

import de.matthesvoss.pingtest.gui.theme.ThemeColors;
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

    static ScaledThemedIconButton iconButton(String text, String iconName, double heightFactor, Runnable action) {
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

    static JLabel paddedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        return l;
    }

    static JPanel separatorPanel(LayoutManager layout, Component... components) {
        JPanel p = new JPanel(layout) {
            @Override
            protected void paintChildren(Graphics g) {
                super.paintChildren(g);

                // Paint custom separator
                g.setColor(ThemeColors.separator());
                for (int i = 0; i < getComponentCount() - 1; i++) {
                    Component a = getComponent(i), b = getComponent(i + 1);
                    if (a.getY() == b.getY()) {
                        g.drawLine(b.getX(), a.getY() + 2, b.getX(), a.getY() + a.getHeight() - 2);
                    }
                }
            }
        };
        for (Component c : components) {
            p.add(c);
        }
        return p;
    }
}
