package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.LoginRequest;
import edu.mec.studentportal.client.dto.LoginResponse;
import edu.mec.studentportal.client.ui.theme.Glyph;
import edu.mec.studentportal.client.ui.theme.PortalTheme;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Email/password sign-in with a gradient hero and a floating form card. On success the JWT is
 * stored in memory and navigation happens via the session listener; failures render inline.
 */
public final class LoginView extends JPanel {
    private final transient ApiClient api;
    private final transient SessionContext session;

    private final HeroPanel heroPanel = new HeroPanel();
    private UiKit.ShadowCard card;

    private final UiKit.HintTextField emailField = UiKit.hintField("you@mec.edu");
    private final RoundPasswordField passwordField = new RoundPasswordField("Enter your password");
    private final UiKit.GradientButton loginButton = UiKit.gradientButton("Sign in", Glyph.ARROW_RIGHT);
    private final JLabel statusLabel = new JLabel(" ");

    public LoginView(ApiClient api, SessionContext session) {
        super(new BorderLayout());
        this.api = api;
        this.session = session;
        setBackground(PortalTheme.CANVAS);

        add(heroPanel, BorderLayout.WEST);

        JPanel formArea = new JPanel();
        formArea.setOpaque(false);
        formArea.setLayout(new javax.swing.BoxLayout(formArea, javax.swing.BoxLayout.Y_AXIS));
        formArea.setBorder(new EmptyBorder(30, 40, 30, 40));

        card = UiKit.card(28, 32);
        card.setLayout(new javax.swing.BoxLayout(card, javax.swing.BoxLayout.Y_AXIS));
        card.setAlignmentX(0.5f);
        card.setMaximumSize(new Dimension(430, Integer.MAX_VALUE));
        // with the trailing glue (added after secureRow) this centers the form
        // vertically inside the full-height card instead of pinning it to the top
        card.add(Box.createVerticalGlue());

        JLabel eyebrow = new JLabel("WELCOME BACK");
        eyebrow.setFont(PortalTheme.EYEBROW);
        eyebrow.setForeground(PortalTheme.ACCENT);
        eyebrow.setAlignmentX(0f);

        JLabel title = new JLabel("Sign in to your campus");
        title.setFont(PortalTheme.XL_BOLD);
        title.setForeground(PortalTheme.INK);
        title.setAlignmentX(0f);

        JLabel subtitle = new JLabel("Use your MEC account to continue.");
        subtitle.setFont(PortalTheme.BODY);
        subtitle.setForeground(PortalTheme.MUTED);
        subtitle.setAlignmentX(0f);

        card.add(eyebrow);
        card.add(spacer(8));
        card.add(title);
        card.add(spacer(6));
        card.add(subtitle);
        card.add(spacer(24));

        emailField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        emailField.setAlignmentX(0f);
        JPanel emailBlock = UiKit.fieldStack("Email address", emailField);
        emailBlock.setAlignmentX(0f);
        card.add(emailBlock);
        card.add(spacer(16));

        JPanel passwordRow = new JPanel(new BorderLayout(8, 0));
        passwordRow.setOpaque(false);
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        passwordRow.add(passwordField, BorderLayout.CENTER);
        passwordRow.add(new EyeToggle(passwordField), BorderLayout.EAST);
        passwordRow.setAlignmentX(0f);
        JPanel passwordBlock = UiKit.fieldStack("Password", passwordRow);
        passwordBlock.setAlignmentX(0f);
        card.add(passwordBlock);
        card.add(spacer(22));

        loginButton.setAlignmentX(0f);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        card.add(loginButton);
        card.add(spacer(12));

        statusLabel.setFont(PortalTheme.SM);
        statusLabel.setForeground(PortalTheme.MUTED);
        statusLabel.setAlignmentX(0f);
        card.add(statusLabel);
        card.add(spacer(8));

        JLabel forgot = new JLabel(
            "Forgot password? See your admin with your college ID.");
        forgot.setFont(PortalTheme.XS);
        forgot.setForeground(PortalTheme.MUTED);
        forgot.setAlignmentX(0f);
        card.add(forgot);
        card.add(spacer(10));

        JPanel secureRow = new JPanel(new BorderLayout(6, 0));
        secureRow.setOpaque(false);
        secureRow.add(glyphIcon(Glyph.LOCK, PortalTheme.MUTED), BorderLayout.WEST);
        JLabel secure = new JLabel("Secure access to your campus community");
        secure.setFont(PortalTheme.XS);
        secure.setForeground(PortalTheme.MUTED);
        secureRow.add(secure, BorderLayout.CENTER);
        secureRow.setAlignmentX(0f);
        // cap the row so BoxLayout can't stretch it to fill the tall card; the glue
        // below absorbs the leftover instead
        secureRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        card.add(secureRow);
        card.add(Box.createVerticalGlue());

        formArea.add(Box.createVerticalGlue());
        formArea.add(card);
        formArea.add(Box.createVerticalGlue());
        add(formArea, BorderLayout.CENTER);

        loginButton.addActionListener(event -> signIn());
        javax.swing.KeyStroke enter = javax.swing.KeyStroke.getKeyStroke("ENTER");
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter, "login");
        getActionMap().put("login", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                signIn();
            }
        });

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                applyResponsiveSizes();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        applyResponsiveSizes();
    }

    /**
     * Auto-resize instead of static placements: the hero grows to a third of the
     * window (470-760px) and the sign-in card scales 430-560px, always centered in
     * its column, so maximizing never strands a small card in a sea of canvas.
     */
    private void applyResponsiveSizes() {
        int width = getWidth();
        if (width <= 0 || card == null) {
            return;
        }
        int heroWidth = Math.max(470, Math.min(760, Math.round(width / 3f)));
        heroPanel.setPreferredSize(new Dimension(heroWidth, 0));
        int cardWidth = Math.max(430, Math.min(560, Math.round(width / 5f)));
        card.setMaximumSize(new Dimension(cardWidth, Integer.MAX_VALUE));
        revalidate();
    }

    private void signIn() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (email.isEmpty() || password.isEmpty()) {
            showInline("Enter your email and password.", PortalTheme.WARN);
            return;
        }
        showInline("Signing in…", PortalTheme.MUTED);
        ApiTask.run(
            () -> api.post("/auth/login", new LoginRequest(email, password), LoginResponse.class),
            response -> {
                showInline(" ", PortalTheme.MUTED);
                passwordField.setText("");
                session.setSession(response.accessToken(), response.user().toUserSummary());
            },
            error -> showInline(error.getMessage(), PortalTheme.DANGER),
            loginButton
        );
    }

    private void showInline(String message, Color color) {
        statusLabel.setForeground(color);
        statusLabel.setText(message == null || message.isBlank() ? " " : message);
    }

    public void showStatus(String message) {
        showInline(message, PortalTheme.MUTED);
        SwingUtilities.invokeLater(emailField::requestFocusInWindow);
    }

    public void clearInputs() {
        emailField.setText("");
        passwordField.setText("");
    }

    private static Box.Filler spacer(int height) {
        return new Box.Filler(new Dimension(0, height), new Dimension(0, height),
            new Dimension(Integer.MAX_VALUE, height));
    }

    private static JComponent glyphIcon(Glyph glyph, Color color) {
        return new GlyphLabel(glyph, color, 15);
    }

    // ---------------------------------------------------------------- hero

    /** Navy gradient panel with brand, headline, copy and feature rows. */
    private static final class HeroPanel extends JPanel {
        private HeroPanel() {
            setPreferredSize(new Dimension(470, 0));
            setMinimumSize(new Dimension(380, 0));
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(44, 44, 36, 44));
            setBackground(PortalTheme.HERO_TOP);

            JPanel top = new JPanel(new BorderLayout(12, 0));
            top.setOpaque(false);
            top.add(new SidebarTabs.LogoTile(42), BorderLayout.WEST);
            JLabel brand = new JLabel("MEC PORTAL");
            brand.setForeground(Color.WHITE);
            brand.setFont(PortalTheme.LG_BOLD);
            top.add(brand, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            JPanel middle = new JPanel();
            middle.setOpaque(false);
            middle.setLayout(new javax.swing.BoxLayout(middle, javax.swing.BoxLayout.Y_AXIS));
            JLabel headline = new JLabel("<html>Everything happening<br>on your campus.</html>");
            headline.setForeground(Color.WHITE);
            headline.setFont(PortalTheme.HERO);
            headline.setAlignmentX(0f);
            middle.add(headline);
            middle.add(spacer(16));
            JLabel copy = new JLabel(
                "<html>Discover events, find your community,<br>and make campus life count.</html>");
            copy.setForeground(new Color(0xC6, 0xE6, 0xD2));
            copy.setFont(PortalTheme.MD);
            copy.setAlignmentX(0f);
            middle.add(copy);
            middle.add(spacer(30));
            middle.add(featureRow(Glyph.CALENDAR, "Live event calendar for every role"));
            middle.add(spacer(12));
            middle.add(featureRow(Glyph.USERS, "Clubs, rosters and announcements"));
            middle.add(spacer(12));
            middle.add(featureRow(Glyph.BELL, "Notifications the moment things change"));
            add(middle, BorderLayout.CENTER);

            JLabel footer = new JLabel("STUDENT PORTAL SUITE  •  2026");
            footer.setForeground(PortalTheme.SIDEBAR_TEXT_DIM);
            footer.setFont(PortalTheme.EYEBROW);
            add(footer, BorderLayout.SOUTH);
        }

        private static JPanel featureRow(Glyph glyph, String text) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setAlignmentX(0f);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            row.add(new GlyphLabel(glyph, PortalTheme.CYAN, 18), BorderLayout.WEST);
            JLabel label = new JLabel(text);
            label.setForeground(new Color(0xDD, 0xF2, 0xE5));
            label.setFont(PortalTheme.BODY);
            row.add(label, BorderLayout.CENTER);
            return row;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, PortalTheme.HERO_TOP,
                    w * 0.7f, h, PortalTheme.HERO_MID));
                g2.fillRect(0, 0, w, h);
                g2.setColor(new Color(PortalTheme.HERO_GLOW.getRed(),
                    PortalTheme.HERO_GLOW.getGreen(), PortalTheme.HERO_GLOW.getBlue(), 52));
                g2.fillOval(-w / 3, h - 180, w, 320);
                g2.setColor(new Color(0x0D, 0xBF, 0x77, 46));
                g2.fillOval(w - 190, -130, 300, 300);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class GlyphLabel extends JComponent {
        private final Glyph glyph;
        private final Color color;
        private final int size;

        private GlyphLabel(Glyph glyph, Color color, int size) {
            this.glyph = glyph;
            this.color = color;
            this.size = size;
            setPreferredSize(new Dimension(size + 2, size + 2));
            setMaximumSize(new Dimension(size + 2, size + 2));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                glyph.paint(g2, 1, 1, size, color);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Password field with the same rounded/focus treatment as {@link UiKit.HintTextField}. */
    private static final class RoundPasswordField extends JPasswordField {
        private final String hint;
        private boolean hintVisible = true;

        private RoundPasswordField(String hint) {
            this.hint = hint;
            setFont(PortalTheme.MD);
            setForeground(PortalTheme.INK);
            setCaretColor(PortalTheme.INK);
            setOpaque(false);
            setEchoChar('•');
            setBorder(new EmptyBorder(11, 15, 11, 15));
            setPreferredSize(new Dimension(getPreferredSize().width, 46));
            UiKit.enableTextEditing(this);
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
                    boolean nowEmpty = getPassword().length == 0;
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
            if (hintVisible && !hasFocus() && getPassword().length == 0) {
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

    /** Round icon button that toggles password visibility. */
    private static final class EyeToggle extends JButton {
        private final JPasswordField target;
        private boolean visibleText;

        private EyeToggle(JPasswordField target) {
            this.target = target;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setToolTipText("Show password");
            setPreferredSize(new Dimension(46, 46));
            setMaximumSize(new Dimension(46, 46));
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            addActionListener(event -> {
                visibleText = !visibleText;
                target.setEchoChar(visibleText ? (char) 0 : '\u2022');
                setToolTipText(visibleText ? "Hide password" : "Show password");
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                if (getModel().isRollover()) {
                    UiKit.roundFill(g2, 0, 0, w, h, h / 2.0, PortalTheme.ACCENT_SOFT);
                }
                (visibleText ? Glyph.EYE_OFF : Glyph.EYE)
                    .paint(g2, (w - 19) / 2.0, (h - 19) / 2.0, 19,
                        getModel().isRollover() ? PortalTheme.ACCENT : PortalTheme.MUTED);
            } finally {
                g2.dispose();
            }
        }
    }
}
