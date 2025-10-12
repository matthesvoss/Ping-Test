package de.matthesvoss.pingtest.resources;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ResourceLoader {
    private static final String ICON_DIR = "/de/matthesvoss/pingtest/icons/";
    private static final String ICON_EXTENSION = ".png";
    private static final String[] FRAME_ICON_RESOLUTIONS = {"16", "32", "64", "128"};

    public static BufferedImage loadImage(String imagePath) {
        URL url = ResourceLoader.class.getResource(imagePath);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + imagePath);
        }
        try {
            return ImageIO.read(url);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load image: " + imagePath, e);
        }
    }

    public static List<Image> loadFrameIcons() {
        List<Image> icons = new ArrayList<>();
        for (String resolution : FRAME_ICON_RESOLUTIONS) {
            icons.add(loadImage(ICON_DIR + "frame/icon" + resolution + ICON_EXTENSION));
        }
        return icons;
    }

    public static ImageIcon loadIcon(String iconName) {
        URL url = ResourceLoader.class.getResource(ICON_DIR + iconName + ICON_EXTENSION);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + iconName);
        }
        return new ImageIcon(url);
    }
}
