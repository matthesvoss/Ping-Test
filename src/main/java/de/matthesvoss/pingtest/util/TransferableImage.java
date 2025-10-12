package de.matthesvoss.pingtest.util;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

// Transferable for copying images to clipboard
public class TransferableImage implements Transferable {
    private static final DataFlavor[] FLAVORS = new DataFlavor[]{DataFlavor.imageFlavor};
    private final Image image;

    public TransferableImage(Image image) {
        this.image = image;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.imageFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedOperationException("Unsupported flavor: " + flavor);
        }
        return image;
    }
}
