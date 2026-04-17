package de.matthesvoss.pingtest.resources;

import de.matthesvoss.pingtest.gui.theme.ThemeManager;

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
    private static final Map<String, BufferedImage> imageBuffer = new HashMap<>();

    private static BufferedImage loadImage(String path) {
        BufferedImage image = imageBuffer.getOrDefault(path, null);
        if (image != null) {
            return image;
        }
        URL url = ResourceLoader.class.getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("Image not found: " + path);
        }
        try {
            image = ImageIO.read(url);
            if (image == null) {
                throw new IOException("Unable to read image: " + path);
            }
            imageBuffer.put(path, image);
            return image;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load image: " + path, ex);
        }
    }

    public static List<Image> loadFrameIcons() {
        List<Image> icons = new ArrayList<>();
        for (String resolution : FRAME_ICON_RESOLUTIONS) {
            icons.add(loadImage(ICON_DIR + "app/pingtest_" + resolution + ICON_EXTENSION));
        }
        return icons;
    }

    public static BufferedImage loadThemedIcon(String name) {
        String path = ICON_DIR + (ThemeManager.isDarkTheme() ? "dark" : "light") + "/" + name + ICON_EXTENSION;
        return loadImage(path);
    }
}
