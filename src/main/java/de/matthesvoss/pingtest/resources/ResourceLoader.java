package de.matthesvoss.pingtest.resources;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceLoader {
    private static final String ICON_DIR = "/de/matthesvoss/pingtest/icons/";
    private static final String ICON_EXTENSION = ".png";
    private static final String[] FRAME_ICON_RESOLUTIONS = {"16", "32", "64", "128"};
    private static final String SHARE_LIGHT = "share-light";
    private static final String SHARE_DARK = "share-dark";
    private static final Map<String, BufferedImage> imageBuffer = new HashMap<>();
    private static final Map<String, ImageIcon> iconBuffer = new HashMap<>();

    private static BufferedImage loadImage(String imagePath) {
        BufferedImage image = imageBuffer.getOrDefault(imagePath, null);
        if (image != null) {
            return image;
        }
        URL url = ResourceLoader.class.getResource(imagePath);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + imagePath);
        }
        try {
            image = ImageIO.read(url);
            imageBuffer.put(imagePath, image);
            return image;
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

    private static ImageIcon loadIcon(String iconName) {
        String iconPath = ICON_DIR + iconName + ICON_EXTENSION;
        ImageIcon icon = iconBuffer.getOrDefault(iconPath, null);
        if (icon != null) {
            return icon;
        }
        URL url = ResourceLoader.class.getResource(iconPath);
        if (url == null) {
            throw new IllegalArgumentException("Resource not found: " + iconName);
        }
        icon = new ImageIcon(url);
        iconBuffer.put(iconPath, icon);
        return icon;
    }

    public static ImageIcon loadShareIcon(boolean darkModeActive) {
        return loadIcon(darkModeActive ? SHARE_DARK : SHARE_LIGHT);
    }
}
