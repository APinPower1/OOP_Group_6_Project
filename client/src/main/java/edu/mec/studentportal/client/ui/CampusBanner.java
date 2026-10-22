package edu.mec.studentportal.client.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** A lightweight campus-inspired banner, drawn with Java2D so the client needs no image assets. */
final class CampusBanner extends JPanel {
    CampusBanner() {
        setOpaque(false);
        setBackground(SwingTheme.NAVY);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(420, 420);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        int width = getWidth();
        int height = getHeight();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(24, 42, 65), width, height,
            new Color(28, 83, 91)));
        g.fillRect(0, 0, width, height);

        g.setComposite(AlphaComposite.SrcOver.derive(0.13f));
        g.setColor(new Color(165, 226, 214));
        g.fillOval(width - 215, height - 235, 280, 280);

        g.setComposite(AlphaComposite.SrcOver.derive(0.22f));
        g.setColor(new Color(198, 235, 226));
        int left = width - 190;
        int top = height - 152;
        Polygon roof = new Polygon(
            new int[] { left - 12, left + 96, left + 205 },
            new int[] { top + 31, top - 16, top + 31 },
            3
        );
        g.fillPolygon(roof);
        g.fillRoundRect(left, top + 30, 192, 94, 4, 4);

        g.setComposite(AlphaComposite.SrcOver.derive(0.32f));
        g.setColor(new Color(22, 55, 72));
        for (int column = 0; column < 5; column++) {
            int x = left + 16 + column * 39;
            g.fillRoundRect(x, top + 40, 15, 75, 5, 5);
        }
        g.fillRect(left - 10, top + 119, 212, 9);

        g.setComposite(AlphaComposite.SrcOver.derive(0.2f));
        g.setColor(Color.WHITE);
        g.fillOval(width - 87, 25, 6, 6);
        g.fillOval(width - 54, 51, 4, 4);
        g.fillOval(width - 122, 64, 4, 4);
        g.dispose();
    }
}
