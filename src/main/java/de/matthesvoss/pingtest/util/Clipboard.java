package de.matthesvoss.pingtest.util;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;

public final class Clipboard {
    private Clipboard() {
    }

    public static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    public static void copyToClipboard(Component component) {
        BufferedImage img = Screenshot.takeScreenshot(component);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(img), null);
    }
}
