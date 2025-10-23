package de.matthesvoss.pingtest.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class ScreenshotUtils {
    private ScreenshotUtils() {}

    public static BufferedImage takeScreenshot(Component component) {
        BufferedImage img = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        component.paint(img.createGraphics());
        return img;
    }

    public static void copyToClipboard(Component component) {
        BufferedImage img = takeScreenshot(component);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(img), null);
    }

    public static void saveScreenshot(BufferedImage screenshot, File file) throws IOException {
        // Ensure .png extension
        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getParentFile(), file.getName() + ".png");
        }
        ImageIO.write(screenshot, "png", file);
    }
}
