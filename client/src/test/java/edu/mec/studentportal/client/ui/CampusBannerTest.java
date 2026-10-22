package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class CampusBannerTest {
    @Test
    void illustrationPaintsAtLoginAndDashboardSizes() throws Exception {
        for (Dimension size : new Dimension[] {
            new Dimension(420, 400),
            new Dimension(640, 142)
        }) {
            SwingUtilities.invokeAndWait(() -> {
                CampusBanner banner = new CampusBanner();
                banner.setSize(size);
                BufferedImage image = new BufferedImage(
                    size.width, size.height, BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D graphics = image.createGraphics();
                try {
                    banner.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                assertNotEquals(0, image.getRGB(0, 0));
            });
        }
    }
}
