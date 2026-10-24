package edu.mec.studentportal.client.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Component kit for the refreshed UI: one-call global theme ({@link #install()}), shadowed
 * rounded cards, gradient/ghost/dark buttons, hint fields, chips and avatars — all painted
 * with Java2D so the look does not depend on the platform look-and-feel.
 */
public final class UiKit {
    private static boolean installed;

    private UiKit() {}

    /** Applies fonts, table/dialog/scrollbar styling application-wide. Idempotent. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        put("Label.font", PortalTheme.BODY);
        put("Label.foreground", PortalTheme.INK);
        put("Button.font", PortalTheme.MD_BOLD);
        put("TextField.font", PortalTheme.MD);
        put("PasswordField.font", PortalTheme.MD);
        put("TextArea.font", PortalTheme.BODY);
        put("TextPane.font", PortalTheme.BODY);
        put("Table.font", PortalTheme.BODY);
        put("Table.alternateRowColor", PortalTheme.TABLE_ALT_ROW);
        put("Table.selectionBackground", PortalTheme.TABLE_SELECTION);
        put("Table.selectionForeground", PortalTheme.INK);
        put("Table.gridColor", PortalTheme.HAIRLINE);
        put("Table.background", Color.WHITE);
        put("Table.rowHeight", 32);
        put("TableHeader.font", PortalTheme.SM_BOLD);
        put("TableHeader.foreground", PortalTheme.MUTED);
        put("TableHeader.background", PortalTheme.TABLE_HEADER_BG);
        put("ScrollPane.border", BorderFactory.createEmptyBorder());
        put("ScrollBarUI", ModernScrollBarUI.class.getName());
        put("ToolTip.font", PortalTheme.SM);
        put("ToolTip.background", PortalTheme.INK);
        put("ToolTip.foreground", Color.WHITE);
        put("OptionPane.font", PortalTheme.BODY);
        put("OptionPane.messageForeground", PortalTheme.INK);
        put("OptionPane.background", PortalTheme.CANVAS);
        put("Panel.background", PortalTheme.CARD);
        put("ComboBox.font", PortalTheme.BODY);
        put("CheckBox.font", PortalTheme.BODY);
        put("RadioButton.font", PortalTheme.BODY);
        put("ProgressBar.font", PortalTheme.SM);
        put("ProgressBar.selectionForeground", Color.WHITE);
        put("ProgressBar.selectionBackground", PortalTheme.INK);
    }

    // ---------------------------------------------------------------- components

    /** White rounded card with a soft drop shadow painted inside the component bounds. */
    public static ShadowCard card() {
        return card(22, 24);
    }

    public static ShadowCard card(int paddingY, int paddingX) {
        ShadowCard card = new ShadowCard();
        card.setBorder(new EmptyBorder(paddingY + 6, paddingX + 6, paddingY + 10, paddingX + 6));
        return card;
    }

    /**
     * Full-page content card matching the calendar: white rounded surface with soft shadow,
     * generous inset (bigger than the corner radius so opaque children never clip it), and
     * every embedded table polished — status columns rendered as rounded chips.
     */
    public static JComponent pageCard(JComponent content) {
        ShadowCard card = card(14, 16);
        card.setLayout(new java.awt.BorderLayout());
        polishTables(content);
        card.add(content, java.awt.BorderLayout.CENTER);
        return card;
    }

    /** Recursively applies table polish (status chip renderer) inside a tab's component tree. */
    public static void polishTables(java.awt.Container root) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof javax.swing.JTable table) {
                applyStatusRenderer(table);
                table.setFillsViewportHeight(true);
                table.setIntercellSpacing(new Dimension(0, 0));
            }
            if (child instanceof java.awt.Container container) {
                polishTables(container);
            }
        }
    }

    private static void applyStatusRenderer(javax.swing.JTable table) {
        javax.swing.table.TableModel model = table.getModel();
        for (int column = 0; column < model.getColumnCount(); column++) {
            if ("status".equalsIgnoreCase(model.getColumnName(column))) {
                table.getColumnModel().getColumn(column)
                    .setCellRenderer(new StatusChipRenderer());
            }
        }
    }

    /** Paints a status value as a tight rounded chip, mirroring the calendar's style. */
    public static final class StatusChipRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private String text = "";
        private Color chipForeground = PortalTheme.SLATE;
        private Color chipBackground = PortalTheme.SLATE_SOFT;

        private StatusChipRenderer() {
            setOpaque(false);
            setBorder(new EmptyBorder(3, 8, 3, 8));
            setFont(PortalTheme.SM_BOLD);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table,
            Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
            text = value == null ? "" : value.toString().trim();
            Color[] colors = statusColors(text);
            chipForeground = colors[0];
            chipBackground = colors[1];
            setOpaque(false);
            setForeground(chipForeground);
            return this;
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                if (!text.isEmpty()) {
                    FontMetrics metrics = g2.getFontMetrics(getFont());
                    int chipHeight = Math.min(getHeight() - 6, 26);
                    int chipY = (getHeight() - chipHeight) / 2;
                    int chipWidth = metrics.stringWidth(text) + 20;
                    UiKit.roundFill(g2, 10, chipY, chipWidth, chipHeight, chipHeight / 2.0,
                        chipBackground);
                    g2.setFont(getFont());
                    g2.setColor(chipForeground);
                    int textY = chipY + (chipHeight - metrics.getHeight()) / 2
                        + metrics.getAscent();
                    g2.drawString(text, 20, textY);
                }
            } finally {
                g2.dispose();
            }
        }

        /** Maps a status value to {foreground, background} chip colors. */
        private static Color[] statusColors(String value) {
            String token = value.toUpperCase(java.util.Locale.ROOT);
            if (token.contains("REJECT") || token.contains("FAIL")
                || token.contains("DISABLE") || token.contains("DECLINE")
                || token.contains("SUSPEND") || token.contains("ERROR")) {
                return new Color[] {PortalTheme.DANGER, PortalTheme.DANGER_SOFT};
            }
            if (token.startsWith("INACTIVE") || token.contains("CANCEL")
                || token.contains("ARCHIV") || token.contains("CLOSED")) {
                return new Color[] {PortalTheme.SLATE, PortalTheme.SLATE_SOFT};
            }
            if (token.contains("PENDING") || token.contains("DRAFT")
                || token.contains("WAIT")) {
                return new Color[] {PortalTheme.WARN, PortalTheme.WARN_SOFT};
            }
            if (token.contains("APPROVED") || token.contains("ACTIVE")
                || token.contains("ENABLED") || token.contains("CONFIRMED")
                || token.contains("REGISTERED") || token.contains("JOINED")
                || token.contains("ACCEPTED") || token.contains("PRESENT")) {
                return new Color[] {PortalTheme.SUCCESS, PortalTheme.SUCCESS_SOFT};
            }
            return new Color[] {PortalTheme.SLATE, PortalTheme.SLATE_SOFT};
        }
    }

    /** Primary call-to-action: emerald→teal gradient, hover lift, white bold label. */
    public static GradientButton gradientButton(String text) {
        return new GradientButton(text, null);
    }

    public static GradientButton gradientButton(String text, Glyph trailing) {
        return new GradientButton(text, trailing);
    }

    /** Outlined button that tints on hover — secondary actions. */
    public static JButton ghostButton(String text) {
        return ghostButton(text, null);
    }

    public static JButton ghostButton(String text, Glyph glyph) {
        GhostButton button = new GhostButton(text, glyph);
        return button;
    }

    /** Dark ink pill button — used for Logout and destructive-adjacent actions. */
    public static JButton darkButton(String text, Glyph glyph) {
        return new DarkButton(text, glyph);
    }

    /** Fully rounded small pill (Today, counts). */
    public static JButton pillButton(String text) {
        PillButton button = new PillButton(text);
        return button;
    }

    /** Text field with rounded border, focus accent and placeholder hint. */
    public static HintTextField hintField(String hint) {
        return new HintTextField(hint);
    }

    /**
     * Makes a text field fully copy/paste-capable: binds the platform shortcut (Cmd on macOS)
     * and Ctrl for copy/cut/paste/select-all, and installs a right-click editing menu.
     * The Metal look-and-feel alone leaves these incomplete on macOS.
     */
    public static void enableTextEditing(javax.swing.text.JTextComponent field) {
        int shortcut;
        try {
            shortcut = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException headless) {
            shortcut = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("mac")
                ? java.awt.event.InputEvent.META_DOWN_MASK
                : java.awt.event.InputEvent.CTRL_DOWN_MASK;
        }
        int ctrl = java.awt.event.InputEvent.CTRL_DOWN_MASK;
        bindTextAction(field, shortcut, java.awt.event.KeyEvent.VK_C, "copy");
        bindTextAction(field, shortcut, java.awt.event.KeyEvent.VK_X, "cut");
        bindTextAction(field, shortcut, java.awt.event.KeyEvent.VK_V, "paste");
        bindTextAction(field, shortcut, java.awt.event.KeyEvent.VK_A, "selectAll");
        if (shortcut != ctrl) {
            bindTextAction(field, ctrl, java.awt.event.KeyEvent.VK_C, "copy");
            bindTextAction(field, ctrl, java.awt.event.KeyEvent.VK_X, "cut");
            bindTextAction(field, ctrl, java.awt.event.KeyEvent.VK_V, "paste");
            bindTextAction(field, ctrl, java.awt.event.KeyEvent.VK_A, "selectAll");
        }

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem cut = new javax.swing.JMenuItem("Cut");
        cut.addActionListener(event -> field.cut());
        javax.swing.JMenuItem copy = new javax.swing.JMenuItem("Copy");
        copy.addActionListener(event -> field.copy());
        javax.swing.JMenuItem paste = new javax.swing.JMenuItem("Paste");
        paste.addActionListener(event -> field.paste());
        javax.swing.JMenuItem selectAll = new javax.swing.JMenuItem("Select all");
        selectAll.addActionListener(event -> field.selectAll());
        menu.add(cut);
        menu.add(copy);
        menu.add(paste);
        menu.addSeparator();
        menu.add(selectAll);
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
                String selected = field.getSelectedText();
                boolean hasSelection = selected != null && !selected.isEmpty();
                cut.setEnabled(hasSelection && field.isEditable());
                copy.setEnabled(hasSelection);
                paste.setEnabled(field.isEditable());
                selectAll.setEnabled(!field.getText().isEmpty());
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) {
                // no-op
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) {
                // no-op
            }
        });
        field.setComponentPopupMenu(menu);
    }

    private static void bindTextAction(javax.swing.text.JTextComponent field, int modifiers,
        int keyCode, String actionKey) {
        field.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
            .put(javax.swing.KeyStroke.getKeyStroke(keyCode, modifiers), actionKey);
    }

    /** Small rounded status chip. */
    public static JLabel chip(String text, Color foreground, Color background) {
        JLabel label = new JLabel(text.toUpperCase()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setOpaque(false);
        label.setForeground(foreground);
        label.setBackground(background);
        label.setFont(PortalTheme.EYEBROW);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new EmptyBorder(5, 10, 5, 10));
        return label;
    }

    /** Gradient circle with initials — user identity avatar. */
    public static JComponent avatar(String name, int size) {
        return new Avatar(name, size);
    }

    /** Row: small caps eyebrow above a bold title. */
    public static JPanel sectionHeader(String eyebrow, String title) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        JLabel top = new JLabel(eyebrow.toUpperCase());
        top.setFont(PortalTheme.EYEBROW);
        top.setForeground(PortalTheme.ACCENT);
        JLabel bottom = new JLabel(title);
        bottom.setFont(PortalTheme.XL_BOLD);
        bottom.setForeground(PortalTheme.INK);
        top.setAlignmentX(0f);
        bottom.setAlignmentX(0f);
        panel.add(top);
        panel.add(bottom);
        return panel;
    }

    /** Vertical stack of a caption label over a component — form rows. */
    public static JPanel fieldStack(String caption, JComponent component) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        JLabel label = new JLabel(caption.toUpperCase());
        label.setFont(PortalTheme.EYEBROW);
        label.setForeground(PortalTheme.MUTED);
        label.setAlignmentX(0f);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        component.setAlignmentX(0f);
        panel.add(label);
        panel.add(new javax.swing.Box.Filler(
            new Dimension(0, 6), new Dimension(0, 6), new Dimension(0, 6)));
        panel.add(component);
        return panel;
    }

    // ---------------------------------------------------------------- painting

    public static void roundFill(Graphics2D g2, double x, double y, double w, double h,
        double radius, Color color) {
        g2.setColor(color);
        g2.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, radius, radius));
    }

    public static void roundStroke(Graphics2D g2, double x, double y, double w, double h,
        double radius, Color color, float stroke) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, radius, radius));
    }

    public static String ellipsize(FontMetrics metrics, String text, int maxWidth) {
        if (text == null || metrics.stringWidth(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String suffix = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return end == 0 ? suffix : text.substring(0, end) + suffix;
    }

    // ---------------------------------------------------------------- inner components

    /** Card that paints a soft drop shadow then a white rounded surface. */
    public static final class ShadowCard extends JPanel {
        private ShadowCard() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                double x = 4;
                double y = 4;
                double w = getWidth() - 8.0;
                double h = getHeight() - 14.0;
                double radius = PortalTheme.RADIUS_CARD;
                Color[] shadowLayers = {
                    new Color(0x1A, 0x2B, 0x24, 16),
                    new Color(0x1A, 0x2B, 0x24, 26),
                };
                for (int i = shadowLayers.length - 1; i >= 0; i--) {
                    double offset = 2.0 * (i + 1);
                    g2.setColor(shadowLayers[i]);
                    g2.fill(new java.awt.geom.RoundRectangle2D.Double(
                        x, y + offset, w, h, radius, radius));
                }
                g2.setColor(PortalTheme.CARD);
                g2.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, radius, radius));
                g2.setColor(new Color(0x1A, 0x2B, 0x24, 14));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new java.awt.geom.RoundRectangle2D.Double(
                    x + 0.5, y + 0.5, w - 1, h - 1, radius, radius));
            } finally {
                g2.dispose();
            }
        }
    }

    public static final class GradientButton extends JButton {
        private final Glyph trailing;

        private GradientButton(String text, Glyph trailing) {
            super(text);
            this.trailing = trailing;
            setForeground(Color.WHITE);
            setFont(PortalTheme.MD_BOLD);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(12, 22, 12, 22));
            setPreferredSize(new Dimension(
                getPreferredSize().width, Math.max(getPreferredSize().height, 46)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                Color start = PortalTheme.ACCENT;
                Color end = PortalTheme.ACCENT_2;
                if (!isEnabled()) {
                    start = new Color(0x9C, 0xB4, 0xA6);
                    end = new Color(0x8A, 0xA4, 0x94);
                } else if (getModel().isPressed()) {
                    start = start.darker();
                    end = end.darker();
                } else if (getModel().isRollover()) {
                    start = lighten(start, 0.10f);
                    end = lighten(end, 0.10f);
                }
                g2.setPaint(new GradientPaint(0, 0, start, w, h, end));
                g2.fill(new java.awt.geom.RoundRectangle2D.Double(
                    1, 2, w - 2, h - 4, PortalTheme.RADIUS_BUTTON, PortalTheme.RADIUS_BUTTON));
                g2.setColor(new Color(0x05, 0x5E, 0x43, 70));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new java.awt.geom.RoundRectangle2D.Double(
                    1.5, 2.5, w - 3, h - 5, PortalTheme.RADIUS_BUTTON, PortalTheme.RADIUS_BUTTON));

                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                String label = getText();
                int textWidth = metrics.stringWidth(label);
                int glyphSpace = trailing == null ? 0 : 20;
                int startX = (w - textWidth - glyphSpace) / 2;
                int textY = (h - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.setColor(getForeground());
                g2.drawString(label, startX, textY);
                if (trailing != null) {
                    trailing.paint(g2, startX + textWidth + 6,
                        (h - 16) / 2.0, 16, getForeground());
                }
            } finally {
                g2.dispose();
            }
        }

        private static Color lighten(Color color, float amount) {
            return new Color(
                Math.min(255, Math.round(color.getRed() + 255 * amount)),
                Math.min(255, Math.round(color.getGreen() + 255 * amount)),
                Math.min(255, Math.round(color.getBlue() + 255 * amount)));
        }
    }

    private static final class GhostButton extends JButton {
        private final Glyph glyph;

        private GhostButton(String text, Glyph glyph) {
            super(text);
            this.glyph = glyph;
            setFont(PortalTheme.BODY_BOLD);
            setForeground(PortalTheme.INK);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 16, 10, 16));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                boolean hover = getModel().isRollover();
                boolean pressed = getModel().isPressed();
                if (hover || pressed) {
                    UiKit.roundFill(g2, 1, 2, w - 2, h - 4, PortalTheme.RADIUS_BUTTON,
                        pressed ? new Color(0xC6, 0xEE, 0xD9) : PortalTheme.ACCENT_SOFT);
                }
                UiKit.roundStroke(g2, 1.5, 2.5, w - 3, h - 5, PortalTheme.RADIUS_BUTTON,
                    hover ? PortalTheme.ACCENT : PortalTheme.BORDER, 1.3f);
                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                String label = getText();
                int textWidth = metrics.stringWidth(label);
                int glyphSpace = glyph == null ? 0 : 20;
                int startX = (w - textWidth - glyphSpace) / 2;
                int textY = (h - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.setColor(hover ? PortalTheme.ACCENT : PortalTheme.INK);
                g2.drawString(label, startX, textY);
                if (glyph != null) {
                    glyph.paint(g2, startX + textWidth + 6, (h - 15) / 2.0, 15,
                        hover ? PortalTheme.ACCENT : PortalTheme.MUTED);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class DarkButton extends JButton {
        private final Glyph glyph;

        private DarkButton(String text, Glyph glyph) {
            super(text);
            this.glyph = glyph;
            setFont(PortalTheme.BODY_BOLD);
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 16, 10, 16));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                Color bg = getModel().isPressed() ? new Color(0x0B, 0x23, 0x1A)
                    : getModel().isRollover() ? new Color(0x24, 0x50, 0x41) : PortalTheme.INK;
                UiKit.roundFill(g2, 1, 2, w - 2, h - 4, PortalTheme.RADIUS_BUTTON, bg);
                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                String label = getText();
                int textWidth = metrics.stringWidth(label);
                int glyphSpace = glyph == null ? 0 : 20;
                int startX = (w - textWidth - glyphSpace) / 2;
                int textY = (h - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.setColor(getForeground());
                g2.drawString(label, startX, textY);
                if (glyph != null) {
                    glyph.paint(g2, startX + textWidth + 6, (h - 15) / 2.0, 15,
                        getForeground());
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class PillButton extends JButton {
        private PillButton(String text) {
            super(text);
            setFont(PortalTheme.SM_BOLD);
            setForeground(PortalTheme.ACCENT);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 14, 7, 14));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                boolean hover = getModel().isRollover();
                UiKit.roundFill(g2, 0, 0, w, h, h / 2.0,
                    hover ? new Color(0xC8, 0xEE, 0xDB) : PortalTheme.ACCENT_SOFT);
                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                g2.setColor(getForeground());
                g2.drawString(getText(), (w - metrics.stringWidth(getText())) / 2f,
                    (h - metrics.getHeight()) / 2f + metrics.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    public static final class HintTextField extends JTextField {
        private final String hint;
        private boolean hintVisible = true;

        private HintTextField(String hint) {
            super();
            this.hint = hint;
            setFont(PortalTheme.MD);
            setForeground(PortalTheme.INK);
            setCaretColor(PortalTheme.INK);
            setOpaque(false);
            setBorder(new EmptyBorder(11, 15, 11, 15));
            setPreferredSize(new Dimension(getPreferredSize().width, 46));
            enableTextEditing(this);
            getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent event) {
                    refresh();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent event) {
                    refresh();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent event) {
                    refresh();
                }

                private void refresh() {
                    boolean nowEmpty = getText().isEmpty();
                    if (hintVisible != nowEmpty) {
                        hintVisible = nowEmpty;
                        repaint();
                    }
                }
            });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent event) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent event) {
                    repaint();
                }
            });
        }

        public String getHint() {
            return hint;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                UiKit.roundFill(g2, 1, 1, w - 2, h - 2, PortalTheme.RADIUS_FIELD, Color.WHITE);
                UiKit.roundStroke(g2, 1, 1, w - 2, h - 2, PortalTheme.RADIUS_FIELD,
                    hasFocus() ? PortalTheme.ACCENT : PortalTheme.BORDER,
                    hasFocus() ? 1.8f : 1.3f);
                if (hasFocus()) {
                    g2.setColor(new Color(PortalTheme.ACCENT.getRed(),
                        PortalTheme.ACCENT.getGreen(), PortalTheme.ACCENT.getBlue(), 40));
                    g2.fill(new java.awt.geom.RoundRectangle2D.Double(3, 3, w - 6, h - 6,
                        PortalTheme.RADIUS_FIELD - 2, PortalTheme.RADIUS_FIELD - 2));
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
            if (hintVisible && !hasFocus() && getText().isEmpty()) {
                Graphics2D hintG2 = (Graphics2D) graphics.create();
                try {
                    hintG2.setFont(PortalTheme.MD);
                    FontMetrics metrics = hintG2.getFontMetrics();
                    hintG2.setColor(PortalTheme.MUTED);
                    hintG2.drawString(hint, 15, (getHeight() - metrics.getHeight()) / 2f
                        + metrics.getAscent());
                } finally {
                    hintG2.dispose();
                }
            }
        }
    }

    private static final class Avatar extends JComponent {
        private final String name;
        private final int size;

        private Avatar(String name, int size) {
            this.name = name;
            this.size = size;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, PortalTheme.ACCENT, size, size,
                    PortalTheme.ACCENT_2));
                g2.fill(new java.awt.geom.Ellipse2D.Double(0, 0, size, size));
                Glyph.initials(g2, 0, 0, size, name, Color.WHITE,
                    PortalTheme.font(Math.max(11, size * 0.38f), java.awt.Font.BOLD));
            } finally {
                g2.dispose();
            }
        }
    }

    /** Slim rounded scroll thumb, borderless track — replaces the chunky default bars. */
    public static final class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            trackColor = new Color(0, 0, 0, 0);
            thumbColor = new Color(0xBD, 0xD3, 0xC4);
            thumbDarkShadowColor = new Color(0, 0, 0, 0);
            thumbHighlightColor = new Color(0, 0, 0, 0);
            thumbLightShadowColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        protected void paintThumb(Graphics graphics, java.awt.Rectangle bounds) {
            if (!scrollbar.isEnabled() || bounds.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = Math.min(8, bounds.width);
                int x = bounds.x + 2;
                int width = Math.max(4, bounds.width - 4);
                g2.setColor(new Color(0xBD, 0xD3, 0xC4));
                g2.fill(new java.awt.geom.RoundRectangle2D.Double(
                    x, bounds.y + 3, width, bounds.height - 6, arc, arc));
            } finally {
                g2.dispose();
            }
        }

        protected void paintTrack(Graphics graphics, java.awt.Rectangle bounds) {
            // transparent track
        }

        private static JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }

    private static void put(String key, Object value) {
        UIManager.put(key, value);
    }

    /** Convenience: wrap a component in a scroll pane without borders. */
    public static JScrollPane scroll(JComponent content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(Color.WHITE);
        return pane;
    }

    /** Utility for sidebar/nav lists: vertical stack with gaps. */
    public static JPanel vstack(int gap, boolean transparent) {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        if (transparent) {
            panel.setOpaque(false);
        }
        if (gap > 0) {
            panel.setBorder(new EmptyBorder(gap, gap, gap, gap));
        }
        return panel;
    }

    /** Exposed for hover cursor handling on decorative labels. */
    public static void handCursor(JComponent component) {
        component.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                // subclasses attach real actions; this only guarantees a hand cursor
            }
        });
    }

    static List<String> splitTwoLines(String text) {
        List<String> lines = new ArrayList<>(2);
        if (text == null || text.isBlank()) {
            return lines;
        }
        String trimmed = text.trim();
        int middle = trimmed.length() / 2;
        int breakAt = trimmed.lastIndexOf(' ', middle);
        if (breakAt <= 0) {
            breakAt = trimmed.indexOf(' ', middle);
        }
        if (breakAt <= 0) {
            lines.add(trimmed);
            return lines;
        }
        lines.add(trimmed.substring(0, breakAt));
        lines.add(trimmed.substring(breakAt + 1));
        return lines;
    }
}
