package edu.mec.studentportal.client.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicButtonUI;

/** Paints lightweight hover, focus and press depth effects for every Swing button. */
public final class InteractiveButtonUI extends BasicButtonUI {
    public static javax.swing.plaf.ComponentUI createUI(JComponent component) {
        return new InteractiveButtonUI();
    }

    @Override
    public void installDefaults(AbstractButton button) {
        super.installDefaults(button);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
    }

    @Override
    public void update(Graphics graphics, JComponent component) {
        paint(graphics, component);
    }

    @Override
    public void paint(Graphics graphics, JComponent component) {
        AbstractButton button = (AbstractButton) component;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int width = button.getWidth();
        int height = button.getHeight();
        int arc = Math.min(14, Math.max(8, height / 3));
        boolean enabled = button.isEnabled();
        boolean pressed = button.getModel().isPressed() && button.getModel().isArmed();
        boolean hovered = enabled && button.getModel().isRollover() && !pressed;
        int lift = hovered ? 1 : 0;
        int shadowDepth = pressed ? 1 : hovered ? 5 : 3;

        Color base = button.getBackground();
        if (!enabled) {
            base = blend(base, SwingTheme.PAGE, 0.55f);
        } else if (hovered) {
            base = blend(base, Color.WHITE, 0.11f);
        } else if (pressed) {
            base = blend(base, SwingTheme.NAVY, 0.10f);
        }

        int x = 3;
        int y = 2 + lift;
        int shapeWidth = Math.max(0, width - 7);
        int shapeHeight = Math.max(0, height - 7);

        g.setColor(enabled ? new Color(25, 28, 48, hovered ? 78 : 48)
            : new Color(25, 28, 48, 20));
        g.fillRoundRect(x + 1, y + shadowDepth, shapeWidth, shapeHeight, arc, arc);

        g.setColor(base);
        g.fillRoundRect(x, y, shapeWidth, shapeHeight, arc, arc);
        g.setColor(hovered ? SwingTheme.GOLD
            : enabled ? blend(button.getBackground(), SwingTheme.BORDER, 0.35f)
                : SwingTheme.BORDER);
        g.drawRoundRect(x, y, shapeWidth, shapeHeight, arc, arc);

        if (button.isFocusOwner()) {
            g.setColor(SwingTheme.ACCENT);
            g.drawRoundRect(x - 2, y - 1, shapeWidth + 4, shapeHeight + 2,
                arc + 2, arc + 2);
        }

        if (pressed) {
            g.translate(0, 2);
        } else if (hovered) {
            g.translate(0, -1);
        }
        super.paint(g, component);
        g.dispose();
    }

    private static Color blend(Color first, Color second, float amount) {
        float inverse = 1f - amount;
        return new Color(
            Math.round(first.getRed() * inverse + second.getRed() * amount),
            Math.round(first.getGreen() * inverse + second.getGreen() * amount),
            Math.round(first.getBlue() * inverse + second.getBlue() * amount),
            first.getAlpha()
        );
    }
}
