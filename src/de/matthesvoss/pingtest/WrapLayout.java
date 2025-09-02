package de.matthesvoss.pingtest;

import java.awt.*;

// Add a wrapping layout that computes preferred size using the parent's current width.
// Based on the well-known approach by Rob Camick.
public class WrapLayout extends FlowLayout {
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int hgap = getHgap();
            int vgap = getVgap();

            Insets insets = target.getInsets();
            int availableWidth;

            // Use the parent's width (current allocated width) to decide wrapping.
            Container parent = target.getParent();
            if (parent != null) {
                int parentWidth = parent.getWidth();
                // Account for parent's insets if any
                Insets pin = parent.getInsets();
                if (pin != null) {
                    parentWidth -= (pin.left + pin.right);
                }
                availableWidth = parentWidth - (insets.left + insets.right + hgap * 2);
            } else {
                availableWidth = target.getWidth() - (insets.left + insets.right + hgap * 2);
            }

            // Before first layout pass widths can be 0; avoid premature wrapping
            if (availableWidth <= 0) {
                availableWidth = Integer.MAX_VALUE / 4;
            }

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int count = target.getComponentCount();
            for (int i = 0; i < count; i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;

                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                if (rowWidth == 0 || rowWidth + d.width <= availableWidth) {
                    if (rowWidth > 0) rowWidth += hgap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                } else {
                    // complete current row
                    dim.width = Math.max(dim.width, rowWidth);
                    if (dim.height > 0) dim.height += vgap;
                    dim.height += rowHeight;

                    // start new row
                    rowWidth = d.width;
                    rowHeight = d.height;
                }
            }

            // add the last row
            dim.width = Math.max(dim.width, rowWidth);
            if (rowHeight > 0) {
                if (dim.height > 0) dim.height += vgap;
                dim.height += rowHeight;
            }

            dim.width += insets.left + insets.right + hgap * 2;
            dim.height += insets.top + insets.bottom + vgap * 2;
            return dim;
        }
    }
}
