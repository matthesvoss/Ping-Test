package de.matthesvoss.pingtest.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class Screenshot {
    private Screenshot() {
    }

    public static BufferedImage takeScreenshot(Component component) {
        BufferedImage img = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        component.paint(img.createGraphics());
        return img;
    }

    public static void saveScreenshot(BufferedImage screenshot, File file) throws IOException {
        // Ensure .png extension
        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getParentFile(), file.getName() + ".png");
        }
        ImageIO.write(screenshot, "png", file);
    }
}
