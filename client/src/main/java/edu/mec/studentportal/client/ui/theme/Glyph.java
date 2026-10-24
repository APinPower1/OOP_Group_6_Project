package edu.mec.studentportal.client.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;

/**
 * Hand-drawn vector glyphs — no external icon library (shared baseline forbids new deps).
 * Every glyph draws inside a {@code size × size} box at (x, y) with a consistent 1.8px
 * round-cap stroke. Slightly oversized coordinates compensate for stroke width so glyphs
 * sit optically centered.
 */
public enum Glyph {
    CALENDAR, HOME, STAR, USERS, MEGAPHONE, BELL, CHART, TICKET, LIST, SHIELD,
    LOGOUT, REFRESH, CHEVRON_LEFT, CHEVRON_RIGHT, EYE, EYE_OFF, ARROW_RIGHT,
    CHECK, SEARCH, PLUS, PIN, CLOCK, LOCK, SPARKLE, DOT;

    /** Paints this glyph at (x, y) scaled to {@code size}, stroked/filled in {@code color}. */
    public void paint(Graphics2D graphics, double x, double y, double size, Color color) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            double scale = size / 24.0;
            g2.translate(x, y);
            g2.scale(scale, scale);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (this) {
                case CALENDAR -> calendar(g2);
                case HOME -> home(g2);
                case STAR -> star(g2);
                case USERS -> users(g2);
                case MEGAPHONE -> megaphone(g2);
                case BELL -> bell(g2);
                case CHART -> chart(g2);
                case TICKET -> ticket(g2);
                case LIST -> list(g2);
                case SHIELD -> shield(g2);
                case LOGOUT -> logout(g2);
                case REFRESH -> refresh(g2);
                case CHEVRON_LEFT -> chevron(g2, true);
                case CHEVRON_RIGHT -> chevron(g2, false);
                case EYE -> eye(g2, true);
                case EYE_OFF -> eye(g2, false);
                case ARROW_RIGHT -> arrowRight(g2);
                case CHECK -> check(g2);
                case SEARCH -> search(g2);
                case PLUS -> plus(g2);
                case PIN -> pin(g2);
                case CLOCK -> clock(g2);
                case LOCK -> lock(g2);
                case SPARKLE -> sparkle(g2);
                case DOT -> g2.fill(new Ellipse2D.Double(9.5, 9.5, 5, 5));
            }
        } finally {
            g2.dispose();
        }
    }

    /** Draws one-letter initials centered in a circle of {@code diameter} — used for avatars. */
    public static void initials(Graphics2D graphics, double x, double y, double diameter,
        String name, Color color, Font font) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(font);
            String initials = initialsOf(name);
            FontMetrics metrics = g2.getFontMetrics();
            double tx = x + (diameter - metrics.stringWidth(initials)) / 2.0;
            double ty = y + (diameter - metrics.getHeight()) / 2.0 + metrics.getAscent();
            g2.setColor(color);
            g2.drawString(initials, (float) tx, (float) ty);
        } finally {
            g2.dispose();
        }
    }

    public static String initialsOf(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    private static void calendar(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(3, 5, 18, 16, 3.5, 3.5));
        g.drawLine(3, 10, 21, 10);
        g.drawLine(8, 3, 8, 7);
        g.drawLine(16, 3, 16, 7);
        g.fill(new Ellipse2D.Double(7.5, 13.5, 2, 2));
        g.fill(new Ellipse2D.Double(14.5, 13.5, 2, 2));
        g.fill(new Ellipse2D.Double(7.5, 17, 2, 2));
    }

    private static void home(Graphics2D g) {
        Path2D roof = new Path2D.Double();
        roof.moveTo(3.5, 11.5);
        roof.lineTo(12, 4);
        roof.lineTo(20.5, 11.5);
        g.draw(roof);
        g.draw(new RoundRectangle2D.Double(6, 11.5, 12, 8.5, 1.5, 1.5));
        g.draw(new java.awt.geom.Line2D.Double(10.5, 20, 10.5, 15.5));
        g.draw(new java.awt.geom.Line2D.Double(13.5, 20, 13.5, 15.5));
    }

    private static void star(Graphics2D g) {
        Polygon star = new Polygon();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double radius = i % 2 == 0 ? 9 : 3.9;
            star.addPoint((int) Math.round(12 + radius * Math.cos(angle)),
                (int) Math.round(12.4 + radius * Math.sin(angle)));
        }
        g.draw(star);
    }

    private static void users(Graphics2D g) {
        g.draw(new Ellipse2D.Double(8.5, 4, 7, 7));
        Arc2D shoulders = new Arc2D.Double(4, 13, 16, 12, 0, -180, Arc2D.OPEN);
        g.draw(shoulders);
        g.draw(new Ellipse2D.Double(2.5, 7.5, 5.5, 5.5));
        Arc2D left = new Arc2D.Double(0.5, 15.5, 9, 9, 60, -150, Arc2D.OPEN);
        g.draw(left);
    }

    private static void megaphone(Graphics2D g) {
        Path2D horn = new Path2D.Double();
        horn.moveTo(4, 10.5);
        horn.lineTo(15, 5.5);
        horn.lineTo(15, 18.5);
        horn.lineTo(4, 13.5);
        horn.closePath();
        g.draw(horn);
        g.draw(new RoundRectangle2D.Double(2, 9.5, 3, 5, 1.2, 1.2));
        g.drawLine(8, 14, 8, 20);
        g.drawLine(8, 20, 11, 20);
        g.draw(new Arc2D.Double(17.5, 8, 5, 8, -60, 120, Arc2D.OPEN));
    }

    private static void bell(Graphics2D g) {
        Path2D body = new Path2D.Double();
        body.moveTo(6, 16.5);
        body.quadTo(7.5, 15, 7.5, 10.5);
        body.quadTo(7.5, 5.5, 12, 5.5);
        body.quadTo(16.5, 5.5, 16.5, 10.5);
        body.quadTo(16.5, 15, 18, 16.5);
        body.closePath();
        g.draw(body);
        g.draw(new java.awt.geom.Line2D.Double(4.8, 16.7, 19.2, 16.7));
        g.draw(new Arc2D.Double(10.5, 18.5, 3, 3, 0, -180, Arc2D.OPEN));
        g.draw(new java.awt.geom.Line2D.Double(12, 3.3, 12, 5.3));
    }

    private static void chart(Graphics2D g) {
        g.drawLine(4, 20, 20, 20);
        g.fill(new RoundRectangle2D.Double(5, 11, 3.4, 7, 1.2, 1.2));
        g.fill(new RoundRectangle2D.Double(10.3, 6.5, 3.4, 11.5, 1.2, 1.2));
        g.fill(new RoundRectangle2D.Double(15.6, 9, 3.4, 9, 1.2, 1.2));
    }

    private static void ticket(Graphics2D g) {
        RoundRectangle2D body = new RoundRectangle2D.Double(3, 7, 18, 10, 2.5, 2.5);
        g.draw(body);
        g.draw(new Arc2D.Double(8.5, 4.5, 7, 5, 0, 180, Arc2D.OPEN));
        g.draw(new Arc2D.Double(8.5, 14.5, 7, 5, 180, 180, Arc2D.OPEN));
        g.draw(new java.awt.geom.Line2D.Double(15, 9.5, 15, 14.5));
    }

    private static void list(Graphics2D g) {
        g.fill(new Ellipse2D.Double(4.5, 6, 2.4, 2.4));
        g.fill(new Ellipse2D.Double(4.5, 11, 2.4, 2.4));
        g.fill(new Ellipse2D.Double(4.5, 16, 2.4, 2.4));
        g.draw(new java.awt.geom.Line2D.Double(9.5, 7.2, 20, 7.2));
        g.draw(new java.awt.geom.Line2D.Double(9.5, 12.2, 20, 12.2));
        g.draw(new java.awt.geom.Line2D.Double(9.5, 17.2, 20, 17.2));
    }

    private static void shield(Graphics2D g) {
        Path2D body = new Path2D.Double();
        body.moveTo(12, 3);
        body.lineTo(20, 6.2);
        body.lineTo(20, 12);
        body.quadTo(20, 18, 12, 21.2);
        body.quadTo(4, 18, 4, 12);
        body.lineTo(4, 6.2);
        body.closePath();
        g.draw(body);
        check(g);
    }

    private static void logout(Graphics2D g) {
        g.draw(new Arc2D.Double(3, 4.5, 13, 15, -90, 180, Arc2D.OPEN));
        g.drawLine(10, 12, 21, 12);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(17.5, 8.5);
        arrow.lineTo(21, 12);
        arrow.lineTo(17.5, 15.5);
        g.draw(arrow);
    }

    private static void refresh(Graphics2D g) {
        Arc2D arc = new Arc2D.Double(4, 4, 16, 16, 40, 270, Arc2D.OPEN);
        g.draw(arc);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(14.5, 2.5);
        arrow.lineTo(20, 5.5);
        arrow.lineTo(15.5, 10);
        g.draw(arrow);
    }

    private static void chevron(Graphics2D g, boolean left) {
        Path2D path = new Path2D.Double();
        if (left) {
            path.moveTo(14.5, 5.5);
            path.lineTo(9, 12);
            path.lineTo(14.5, 18.5);
        } else {
            path.moveTo(9.5, 5.5);
            path.lineTo(15, 12);
            path.lineTo(9.5, 18.5);
        }
        g.draw(path);
    }

    private static void eye(Graphics2D g, boolean visible) {
        Path2D almond = new Path2D.Double();
        almond.moveTo(2.5, 12);
        almond.quadTo(12, 3.5, 21.5, 12);
        almond.quadTo(12, 20.5, 2.5, 12);
        almond.closePath();
        g.draw(almond);
        if (visible) {
            g.draw(new Ellipse2D.Double(9, 9, 6, 6));
            g.fill(new Ellipse2D.Double(11, 11, 2, 2));
        } else {
            g.draw(new java.awt.geom.Line2D.Double(4.5, 4.5, 19.5, 19.5));
        }
    }

    private static void arrowRight(Graphics2D g) {
        g.drawLine(4, 12, 19, 12);
        Path2D head = new Path2D.Double();
        head.moveTo(15, 7.5);
        head.lineTo(19.5, 12);
        head.lineTo(15, 16.5);
        g.draw(head);
    }

    private static void check(Graphics2D g) {
        Path2D path = new Path2D.Double();
        path.moveTo(6, 12.5);
        path.lineTo(10.5, 17);
        path.lineTo(18, 8);
        g.draw(path);
    }

    private static void search(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4.5, 4.5, 11, 11));
        g.draw(new java.awt.geom.Line2D.Double(14.5, 14.5, 20, 20));
    }

    private static void plus(Graphics2D g) {
        g.drawLine(12, 5, 12, 19);
        g.drawLine(5, 12, 19, 12);
    }

    private static void pin(Graphics2D g) {
        g.draw(new Ellipse2D.Double(7.5, 4, 9, 9));
        Path2D tail = new Path2D.Double();
        tail.moveTo(8.6, 11.8);
        tail.lineTo(6, 20.5);
        tail.lineTo(12, 17);
        tail.lineTo(18, 20.5);
        tail.lineTo(15.4, 11.8);
        g.draw(tail);
        g.fill(new Ellipse2D.Double(10.5, 6.5, 3, 3));
    }

    private static void clock(Graphics2D g) {
        g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
        Path2D hands = new Path2D.Double();
        hands.moveTo(12, 7);
        hands.lineTo(12, 12);
        hands.lineTo(15.5, 14.5);
        g.draw(hands);
    }

    private static void lock(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(5.5, 10.5, 13, 9.5, 2.5, 2.5));
        Arc2D shackle = new Arc2D.Double(8, 4.5, 8, 11, 0, 180, Arc2D.OPEN);
        g.draw(shackle);
        g.fill(new Ellipse2D.Double(11, 13.5, 2, 2));
    }

    private static void sparkle(Graphics2D g) {
        Path2D star = new Path2D.Double();
        star.moveTo(12, 3);
        star.quadTo(13, 10.5, 21, 12);
        star.quadTo(13, 13.5, 12, 21);
        star.quadTo(11, 13.5, 3, 12);
        star.quadTo(11, 10.5, 12, 3);
        star.closePath();
        g.fill(star);
    }
}
