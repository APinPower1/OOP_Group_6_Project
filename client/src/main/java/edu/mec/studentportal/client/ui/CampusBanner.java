package edu.mec.studentportal.client.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import javax.swing.JPanel;

/** A scalable, original campus illustration that requires no external image assets. */
final class CampusBanner extends JPanel {
    CampusBanner() {
        setOpaque(true);
        setBackground(SwingTheme.NAVY);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(420, 420);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        int width = getWidth();
        int height = getHeight();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setPaint(new GradientPaint(
            0, 0, new Color(31, 31, 66),
            width, height, new Color(61, 52, 95)
        ));
        g.fillRect(0, 0, width, height);

        g.setColor(new Color(255, 200, 112, 24));
        g.fill(new Ellipse2D.Double(width * 0.55, -height * 0.62,
            width * 0.8, height * 1.2));
        g.setColor(new Color(253, 45, 52, 30));
        g.fill(new Ellipse2D.Double(width * 0.72, -height * 0.32,
            width * 0.56, height * 0.82));

        g.setColor(new Color(255, 255, 255, 34));
        for (int index = 0; index < 5; index++) {
            int x = (int) (width * (0.58 + index * 0.075));
            int y = (int) (height * (0.15 + (index % 2) * 0.11));
            g.fillOval(x, y, Math.max(2, width / 110), Math.max(2, width / 110));
        }

        int ground = (int) (height * 0.86);
        Polygon farHill = new Polygon(
            new int[] { 0, (int) (width * 0.28), (int) (width * 0.55),
                (int) (width * 0.82), width, width, 0 },
            new int[] { ground, (int) (height * 0.76), (int) (height * 0.82),
                (int) (height * 0.72), (int) (height * 0.78), height, height },
            7
        );
        g.setColor(new Color(255, 255, 255, 18));
        g.fillPolygon(farHill);

        int buildingWidth = Math.max(118, Math.min((int) (width * 0.45), 235));
        int buildingHeight = Math.max(34, Math.min((int) (height * 0.37), 142));
        int left = width - buildingWidth - Math.max(14, width / 18);
        int roofTop = ground - buildingHeight;
        int roofPeak = Math.max(roofTop - Math.max(8, buildingHeight / 5), height / 5);
        Polygon roof = new Polygon(
            new int[] { left - 9, left + buildingWidth / 2, left + buildingWidth + 9 },
            new int[] { roofTop + 10, roofPeak, roofTop + 10 },
            3
        );
        g.setColor(new Color(255, 200, 112, 210));
        g.fillPolygon(roof);
        g.setColor(new Color(255, 255, 255, 216));
        g.fillRect(left, roofTop + 9, buildingWidth, buildingHeight);
        g.setColor(new Color(42, 42, 83, 220));
        int columns = 5;
        int gap = Math.max(5, buildingWidth / 28);
        int columnWidth = (buildingWidth - gap * (columns + 1)) / columns;
        for (int column = 0; column < columns; column++) {
            int x = left + gap + column * (columnWidth + gap);
            g.fillRoundRect(x, roofTop + 16, columnWidth,
                Math.max(12, buildingHeight - 25), 5, 5);
        }
        g.setColor(new Color(255, 200, 112));
        g.fillRect(left - 5, ground - 5, buildingWidth + 10, 6);

        g.setColor(new Color(253, 45, 52, 180));
        g.fillRoundRect(left + buildingWidth - 26, ground - 13, 13, 6, 6, 6);
        g.dispose();
    }
}
