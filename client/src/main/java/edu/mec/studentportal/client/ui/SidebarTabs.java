package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.UserSummary;
import edu.mec.studentportal.client.ui.theme.Glyph;
import edu.mec.studentportal.client.ui.theme.PortalTheme;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Left-rail navigation hosting one content card per tab (built by the dashboards with the
 * same {@code addTab}/{@code setSelectedIndex} surface they had on {@code JTabbedPane}).
 * Ink-green gradient rail, icon nav items with an accent indicator, user identity at the
 * bottom. Every non-calendar tab is wrapped in a white page card so all pages share the
 * calendar's card language.
 */
public final class SidebarTabs extends JPanel {
    private static final Map<String, Glyph> ICONS = new HashMap<>();

    static {
        ICONS.put("calendar", Glyph.CALENDAR);
        ICONS.put("events", Glyph.STAR);
        ICONS.put("my events", Glyph.STAR);
        ICONS.put("review queue", Glyph.SHIELD);
        ICONS.put("event moderation", Glyph.SHIELD);
        ICONS.put("my registrations", Glyph.TICKET);
        ICONS.put("clubs", Glyph.USERS);
        ICONS.put("rosters", Glyph.LIST);
        ICONS.put("announcements", Glyph.MEGAPHONE);
        ICONS.put("notifications", Glyph.BELL);
        ICONS.put("feedback", Glyph.STAR);
        ICONS.put("event feedback", Glyph.STAR);
        ICONS.put("users", Glyph.USERS);
        ICONS.put("reports", Glyph.CHART);
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel rail = new NavyRail();
    private final JPanel navStack = new JPanel();
    private final List<NavItem> items = new ArrayList<>();
    private int selectedIndex = -1;

    public SidebarTabs(SessionContext session) {
        super(new BorderLayout());
        setBackground(PortalTheme.CANVAS);

        rail.setPreferredSize(new Dimension(PortalTheme.SIDEBAR_WIDTH, 0));
        rail.setBorder(new EmptyBorder(22, 14, 18, 14));
        rail.add(createBrand(session), BorderLayout.NORTH);

        navStack.setOpaque(false);
        navStack.setLayout(new javax.swing.BoxLayout(navStack, javax.swing.BoxLayout.Y_AXIS));
        navStack.setBorder(new EmptyBorder(18, 0, 0, 0));
        rail.add(navStack, BorderLayout.CENTER);

        rail.add(createIdentity(session), BorderLayout.SOUTH);

        content.setBackground(PortalTheme.CANVAS);
        add(rail, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
    }

    /** Adds a navigation item and its content card; first tab becomes the active one. */
    public void addTab(String title, Component panel) {
        NavItem item = new NavItem(title, iconFor(title));
        item.setAlignmentX(0f);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        items.add(item);
        int index = items.size() - 1;
        item.addActionListener(event -> setSelectedIndex(index));
        navStack.add(item);
        if ("Calendar".equals(title) || !(panel instanceof JComponent component)) {
            content.add(panel, title);
        } else {
            content.add(UiKit.pageCard(component), title);
        }
        if (selectedIndex < 0) {
            setSelectedIndex(0);
        }
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        selectedIndex = index;
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setActive(i == index);
        }
        cards.show(content, items.get(index).title);
        repaint();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getSelectedTitle() {
        return selectedIndex < 0 ? "" : items.get(selectedIndex).title;
    }

    private static Glyph iconFor(String title) {
        return ICONS.getOrDefault(title == null ? "" : title.toLowerCase(Locale.ROOT),
            Glyph.DOT);
    }

    private JComponent createBrand(SessionContext session) {
        JPanel brand = new JPanel(new BorderLayout(0, 12));
        brand.setOpaque(false);

        JPanel mark = new JPanel(new BorderLayout(10, 0));
        mark.setOpaque(false);
        mark.add(new LogoTile(), BorderLayout.WEST);
        JLabel name = new JLabel("MEC PORTAL");
        name.setForeground(Color.WHITE);
        name.setFont(PortalTheme.LG_BOLD);
        mark.add(name, BorderLayout.CENTER);
        brand.add(mark, BorderLayout.NORTH);

        String role = "";
        try {
            if (session != null && session.isLoggedIn()) {
                role = session.getRole().name();
            }
        } catch (RuntimeException ignored) {
            role = "";
        }
        if (!role.isEmpty()) {
            JLabel chip = new JLabel(role + " WORKSPACE") {
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
            chip.setOpaque(false);
            chip.setForeground(PortalTheme.CYAN);
            chip.setBackground(new Color(0x4F, 0xEA, 0xC0, 44));
            chip.setFont(PortalTheme.EYEBROW);
            chip.setHorizontalAlignment(SwingConstants.CENTER);
            chip.setBorder(new EmptyBorder(5, 10, 5, 10));
            JPanel chipRow = new JPanel();
            chipRow.setOpaque(false);
            chipRow.setLayout(new javax.swing.BoxLayout(chipRow, javax.swing.BoxLayout.Y_AXIS));
            chip.setAlignmentX(0f);
            chipRow.add(chip);
            brand.add(chipRow, BorderLayout.SOUTH);
        }
        return brand;
    }

    private JComponent createIdentity(SessionContext session) {
        JPanel identity = new JPanel(new BorderLayout(10, 0));
        identity.setOpaque(false);
        identity.setBorder(new EmptyBorder(14, 4, 0, 4));

        UserSummary user = null;
        try {
            user = session == null ? null : session.getUser();
        } catch (RuntimeException ignored) {
            user = null;
        }
        String fullName = user == null ? "Signed out" : user.fullName();
        String email = user == null ? "" : user.email();

        identity.add(UiKit.avatar(fullName, 36), BorderLayout.WEST);
        JPanel texts = new JPanel(new BorderLayout(0, 2));
        texts.setOpaque(false);
        JLabel name = new JLabel(fullName);
        name.setForeground(Color.WHITE);
        name.setFont(PortalTheme.SM_BOLD);
        JLabel mail = new JLabel(email);
        mail.setForeground(PortalTheme.SIDEBAR_TEXT_DIM);
        mail.setFont(PortalTheme.XS);
        texts.add(name, BorderLayout.NORTH);
        texts.add(mail, BorderLayout.SOUTH);
        identity.add(texts, BorderLayout.CENTER);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(12, 0, 0, 0));
        java.awt.Component filler = new javax.swing.Box.Filler(
            new Dimension(0, 1), new Dimension(0, 1),
            new Dimension(Integer.MAX_VALUE, 1));
        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);
        line.add(filler, BorderLayout.CENTER);
        wrap.add(line, BorderLayout.NORTH);
        wrap.add(identity, BorderLayout.SOUTH);
        return wrap;
    }

    /** Sidebar rail: ink-green vertical gradient behind brand, nav and identity. */
    private static final class NavyRail extends JPanel {
        private NavyRail() {
            super(new BorderLayout());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setPaint(new GradientPaint(0, 0, PortalTheme.SIDEBAR_TOP,
                    0, getHeight(), PortalTheme.SIDEBAR_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }
    }

    private final class NavItem extends JButton {
        private final String title;
        private final Glyph icon;
        private boolean active;

        private NavItem(String title, Glyph icon) {
            super(title);
            this.title = title;
            this.icon = icon;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setForeground(PortalTheme.SIDEBAR_TEXT);
            setFont(PortalTheme.MD);
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setRolloverEnabled(true);
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent event) {
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent event) {
                    repaint();
                }
            });
        }

        private void setActive(boolean active) {
            this.active = active;
            repaint();
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
                if (active) {
                    UiKit.roundFill(g2, 0, 1, w, h - 2, 12, new Color(0xFF, 0xFF, 0xFF, 26));
                    g2.setPaint(new GradientPaint(0, 2, PortalTheme.ACCENT, 0, h - 2,
                        PortalTheme.ACCENT_2));
                    g2.fill(new java.awt.geom.RoundRectangle2D.Double(0, 6, 3.5, h - 12,
                        3, 3));
                    g2.setPaint(new GradientPaint(0, 2, PortalTheme.ACCENT, 0, h - 2,
                        PortalTheme.ACCENT_2));
                } else if (hover) {
                    UiKit.roundFill(g2, 0, 1, w, h - 2, 12, PortalTheme.SIDEBAR_HOVER);
                }
                Color iconColor = active ? PortalTheme.CYAN
                    : hover ? Color.WHITE : PortalTheme.SIDEBAR_TEXT;
                icon.paint(g2, 14, (h - 17) / 2.0, 17, iconColor);
                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                g2.setColor(active ? Color.WHITE : getForeground());
                g2.drawString(getText(), 42, (h - metrics.getHeight()) / 2f + metrics.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    /** Rounded gradient app mark with a sparkle glyph — sidebar + login brand logo. */
    public static final class LogoTile extends JComponent {
        public LogoTile() {
            this(34);
        }

        public LogoTile(int size) {
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
                int size = getWidth();
                g2.setPaint(new GradientPaint(0, 0, PortalTheme.ACCENT, size, size,
                    PortalTheme.ACCENT_2));
                g2.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, size, size,
                    size * 0.32, size * 0.32));
                Glyph.SPARKLE.paint(g2, size * 0.22, size * 0.22, size * 0.56, Color.WHITE);
            } finally {
                g2.dispose();
            }
        }
    }
}
