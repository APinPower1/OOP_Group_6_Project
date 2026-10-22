package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class InteractiveButtonUITest {
    @Test
    void themeUsesInteractiveButtonAndPaintsHoverAndPressStates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SwingTheme.install();
            JButton button = new JButton("Continue");
            button.setBackground(SwingTheme.ACCENT);
            button.setForeground(java.awt.Color.WHITE);
            button.setSize(160, 52);

            assertTrue(button.getUI() instanceof InteractiveButtonUI);

            BufferedImage idle = paint(button);
            button.getModel().setRollover(true);
            BufferedImage hovered = paint(button);
            button.getModel().setArmed(true);
            button.getModel().setPressed(true);
            BufferedImage pressed = paint(button);

            assertNotEquals(0, differingPixels(idle, hovered),
                "hover state should visibly change the button");
            assertNotEquals(0, differingPixels(hovered, pressed),
                "press state should visibly change the button");
        });
    }

    private static BufferedImage paint(JButton button) {
        BufferedImage image = new BufferedImage(
            button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            button.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int differingPixels(BufferedImage first, BufferedImage second) {
        int differences = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    differences++;
                }
            }
        }
        return differences;
    }
}
