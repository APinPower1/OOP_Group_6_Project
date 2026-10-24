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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
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
 * MEC email/password sign-in with a light split layout and a hand-drawn campus illustration.
 * On success the JWT is stored in memory and navigation happens via the session listener;
 * failures render inline.
 */
public final class LoginView extends JPanel {
    private final transient ApiClient api;
    private final transient SessionContext session;

    private final HeroPanel heroPanel = new HeroPanel();
    private UiKit.ShadowCard card;

    private final LoginEmailField emailField = new LoginEmailField("you@mec.edu");
    private final RoundPasswordField passwordField = new RoundPasswordField("Enter your password");
    private final LoginButton loginButton = new LoginButton();
    private final JLabel statusLabel = new JLabel(" ");

    public LoginView(ApiClient api, SessionContext session) {
        super(new BorderLayout());
        this.api = api;
        this.session = session;
        setBackground(LoginPalette.CANVAS);
        // stable component names so tests (and tools) can find the fields
        emailField.setName("login.email");
        passwordField.setName("login.password");

        add(heroPanel, BorderLayout.WEST);

        JPanel formArea = new JPanel(new GridBagLayout());
        formArea.setOpaque(false);
        formArea.setBorder(new EmptyBorder(28, 24, 28, 24));

        card = UiKit.card(30, 30);
        card.setLayout(new javax.swing.BoxLayout(card, javax.swing.BoxLayout.Y_AXIS));
        card.setAlignmentX(0.5f);
        card.setMaximumSize(new Dimension(448, Integer.MAX_VALUE));

        JLabel eyebrow = new JLabel("MEC STUDENT PORTAL");
        eyebrow.setFont(PortalTheme.EYEBROW);
        eyebrow.setForeground(LoginPalette.RED);
        eyebrow.setAlignmentX(0f);

        JLabel title = new JLabel("Welcome back");
        title.setFont(PortalTheme.font(29f, java.awt.Font.BOLD));
        title.setForeground(LoginPalette.INK);
        title.setAlignmentX(0f);

        JLabel subtitle = new JLabel("Sign in with your MEC account to continue.");
        subtitle.setFont(PortalTheme.BODY);
        subtitle.setForeground(LoginPalette.MUTED);
        subtitle.setAlignmentX(0f);

        card.add(eyebrow);
        card.add(spacer(10));
        card.add(title);
        card.add(spacer(7));
        card.add(subtitle);
        card.add(spacer(28));

        emailField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        emailField.setAlignmentX(0f);
        JPanel emailBlock = loginFieldBlock("Email address", emailField);
        emailBlock.setAlignmentX(0f);
        card.add(emailBlock);
        card.add(spacer(19));

        JPanel passwordRow = new JPanel(new BorderLayout(8, 0));
        passwordRow.setOpaque(false);
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        passwordRow.add(passwordField, BorderLayout.CENTER);
        EyeToggle eyeToggle = new EyeToggle(passwordField);
        eyeToggle.setName("login.password.visibility");
        passwordRow.add(eyeToggle, BorderLayout.EAST);
        passwordRow.setAlignmentX(0f);
        JPanel passwordBlock = loginFieldBlock("Password", passwordRow);
        passwordBlock.setAlignmentX(0f);
        card.add(passwordBlock);
        card.add(spacer(24));

        loginButton.setAlignmentX(0f);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        card.add(loginButton);
        card.add(spacer(13));

        statusLabel.setFont(PortalTheme.SM);
        statusLabel.setForeground(LoginPalette.MUTED);
        statusLabel.setName("login.status");
        statusLabel.setAlignmentX(0f);
        card.add(statusLabel);
        card.add(spacer(10));

        JLabel forgot = new JLabel(
            "Forgot password? See your admin with your college ID.");
        forgot.setFont(PortalTheme.XS);
        forgot.setForeground(LoginPalette.MUTED);
        forgot.setAlignmentX(0f);
        card.add(forgot);
        card.add(spacer(18));

        JPanel secureRow = new JPanel(new BorderLayout(6, 0));
        secureRow.setOpaque(false);
        secureRow.add(glyphIcon(Glyph.LOCK, LoginPalette.MUTED), BorderLayout.WEST);
        JLabel secure = new JLabel("Secure access to your campus community");
        secure.setFont(PortalTheme.XS);
        secure.setForeground(LoginPalette.MUTED);
        secureRow.add(secure, BorderLayout.CENTER);
        secureRow.setAlignmentX(0f);
        secureRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        card.add(secureRow);
        Dimension cardSize = card.getPreferredSize();
        card.setPreferredSize(new Dimension(432, cardSize.height));
        card.setMaximumSize(new Dimension(448, cardSize.height));
        formArea.add(card);
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
     * Keep the illustration and form close to a balanced split while leaving enough
     * space for the sign-in card at the app's minimum window size.
     */
    private void applyResponsiveSizes() {
        int width = getWidth();
        if (width <= 0 || card == null) {
            return;
        }
        int heroWidth = Math.max(500, Math.min(780, Math.round(width / 2f)));
        heroPanel.setPreferredSize(new Dimension(heroWidth, 0));
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

    JButton getLoginButton() {
        return loginButton;
    }

    private static Box.Filler spacer(int height) {
        return new Box.Filler(new Dimension(0, height), new Dimension(0, height),
            new Dimension(Integer.MAX_VALUE, height));
    }

    private static JPanel loginFieldBlock(String caption, JComponent component) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new javax.swing.BoxLayout(block, javax.swing.BoxLayout.Y_AXIS));
        JLabel label = new JLabel(caption);
        label.setFont(PortalTheme.BODY_BOLD);
        label.setForeground(LoginPalette.INK);
        label.setAlignmentX(0f);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        component.setAlignmentX(0f);
        block.add(label);
        block.add(new Box.Filler(new Dimension(0, 7), new Dimension(0, 7),
            new Dimension(0, 7)));
        block.add(component);
        return block;
    }

    private static JComponent glyphIcon(Glyph glyph, Color color) {
        return new GlyphLabel(glyph, color, 15);
    }

    /** Login surfaces follow the shared MEC red and white portal palette. */
    private static final class LoginPalette {
        private static final Color CANVAS = new Color(0xFF, 0xF9, 0xF9);
        private static final Color INK = PortalTheme.INK;
        private static final Color MUTED = PortalTheme.MUTED;
        private static final Color RED = PortalTheme.ACCENT;
        private static final Color FIELD = new Color(0xFA, 0xFA, 0xFB);
        private static final Color FIELD_BORDER = PortalTheme.BORDER;
        private static final Color FIELD_FOCUS = PortalTheme.ACCENT;
        private static final Color BUTTON = PortalTheme.ACCENT;

        private LoginPalette() {}
    }

    /** Light, rounded email input with a muted hint and a clear keyboard focus state. */
    private static final class LoginEmailField extends JTextField {
        private final String hint;

        private LoginEmailField(String hint) {
            this.hint = hint;
            setFont(PortalTheme.MD);
            setForeground(LoginPalette.INK);
            setCaretColor(LoginPalette.INK);
            setOpaque(false);
            setBorder(new EmptyBorder(12, 15, 12, 15));
            setPreferredSize(new Dimension(250, 48));
            UiKit.enableTextEditing(this);
            getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent event) {
                    repaint();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent event) {
                    repaint();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent event) {
                    repaint();
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
                UiKit.roundFill(g2, 1, 1, getWidth() - 2, getHeight() - 2,
                    12, hasFocus() ? Color.WHITE : LoginPalette.FIELD);
                UiKit.roundStroke(g2, 1, 1, getWidth() - 2, getHeight() - 2, 12,
                    hasFocus() ? LoginPalette.FIELD_FOCUS : LoginPalette.FIELD_BORDER,
                    hasFocus() ? 1.7f : 1.1f);
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
            if (getText().isEmpty() && !hasFocus()) {
                Graphics2D hintG2 = (Graphics2D) graphics.create();
                try {
                    hintG2.setFont(getFont());
                    FontMetrics metrics = hintG2.getFontMetrics();
                    hintG2.setColor(new Color(0x9A, 0xA1, 0x9C));
                    hintG2.drawString(hint, 15, (getHeight() - metrics.getHeight()) / 2f
                        + metrics.getAscent());
                } finally {
                    hintG2.dispose();
                }
            }
        }
    }

    /** Dark rounded action button that preserves Swing's normal enabled/loading state. */
    private static final class LoginButton extends UiKit.AnimatedButton {
        private LoginButton() {
            super("Sign in");
            setForeground(Color.WHITE);
            setFont(PortalTheme.MD_BOLD);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setBorder(new EmptyBorder(12, 22, 12, 22));
            setPreferredSize(new Dimension(180, 50));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(0, pressAmount() * 1.0);
                paintHoverShadow(g2, getWidth(), getHeight(), 16);
                Color fill = UiKit.blend(LoginPalette.BUTTON,
                    new Color(0xE7, 0x3A, 0x41), hoverAmount());
                fill = UiKit.blend(fill, new Color(0xA9, 0x17, 0x1D), pressAmount());
                if (!isEnabled()) {
                    fill = new Color(0xB8, 0xB9, 0xBE);
                }
                UiKit.roundFill(g2, 1, 2, getWidth() - 2, getHeight() - 4,
                    16, fill);
                g2.setFont(getFont());
                FontMetrics metrics = g2.getFontMetrics();
                String label = getText();
                int textWidth = metrics.stringWidth(label);
                int centerX = (getWidth() - textWidth - 20) / 2;
                int textY = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.setColor(getForeground());
                g2.drawString(label, centerX, textY);
                Glyph.ARROW_RIGHT.paint(g2, getWidth() - 34,
                    (getHeight() - 16) / 2.0, 16, getForeground());
                paintFocusRing(g2, getWidth(), getHeight(), 16);
            } finally {
                g2.dispose();
            }
        }
    }

    // ---------------------------------------------------------------- illustration panel

    /** Light brand panel with a small original campus illustration in MEC colors. */
    private static final class HeroPanel extends JPanel {
        private HeroPanel() {
            setPreferredSize(new Dimension(530, 0));
            setMinimumSize(new Dimension(430, 0));
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(38, 40, 34, 40));
            setBackground(LoginPalette.CANVAS);

            JPanel top = new JPanel(new BorderLayout(13, 0));
            top.setOpaque(false);
            top.add(new SidebarTabs.LogoTile(40), BorderLayout.WEST);
            JPanel brandStack = new JPanel();
            brandStack.setOpaque(false);
            brandStack.setLayout(new javax.swing.BoxLayout(brandStack,
                javax.swing.BoxLayout.Y_AXIS));
            JLabel brand = new JLabel("MEC THRIKKAKARA");
            brand.setForeground(LoginPalette.INK);
            brand.setFont(PortalTheme.SM_BOLD);
            JLabel portal = new JLabel("STUDENT PORTAL");
            portal.setForeground(LoginPalette.MUTED);
            portal.setFont(PortalTheme.XS);
            brandStack.add(Box.createVerticalGlue());
            brandStack.add(brand);
            brandStack.add(Box.createRigidArea(new Dimension(0, 3)));
            brandStack.add(portal);
            brandStack.add(Box.createVerticalGlue());
            top.add(brandStack, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            add(new IllustrationCanvas(), BorderLayout.CENTER);

            JPanel caption = new JPanel();
            caption.setOpaque(false);
            caption.setLayout(new javax.swing.BoxLayout(caption,
                javax.swing.BoxLayout.Y_AXIS));
            JLabel headline = new JLabel("Make campus yours.");
            headline.setForeground(LoginPalette.INK);
            headline.setFont(PortalTheme.font(25f, java.awt.Font.BOLD));
            JLabel copy = new JLabel("Everything happening at MEC, in one place.");
            copy.setForeground(LoginPalette.MUTED);
            copy.setFont(PortalTheme.BODY);
            caption.add(headline);
            caption.add(Box.createRigidArea(new Dimension(0, 6)));
            caption.add(copy);
            add(caption, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(LoginPalette.CANVAS);
                g2.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }
    }

    /** Self-contained vector illustration, scaled to fit the available left panel. */
    private static final class IllustrationCanvas extends JComponent {
        private IllustrationCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(530, 440));
            setMinimumSize(new Dimension(360, 270));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
                double scale = Math.min(getWidth() / 560.0, getHeight() / 430.0);
                double x = (getWidth() - 560 * scale) / 2.0;
                double y = (getHeight() - 430 * scale) / 2.0;
                g2.translate(x, y);
                g2.scale(scale, scale);
                paintScene(g2);
            } finally {
                g2.dispose();
            }
        }

        private static void paintScene(Graphics2D g) {
            // Soft paper cutout shapes behind the two students.
            g.setPaint(new GradientPaint(62, 50, new Color(0xFD, 0xF0, 0xF1),
                500, 390, new Color(0xF3, 0xF4, 0xF6)));
            g.fill(new RoundRectangle2D.Double(37, 40, 486, 346, 126, 126));
            g.setColor(new Color(0xF7, 0xD9, 0xDB));
            g.fillOval(407, 60, 78, 78);
            g.setColor(new Color(0xF7, 0xEF, 0xE6));
            g.fillOval(76, 285, 68, 68);

            // Floating leaves and sparkle marks.
            g.setColor(new Color(0xB9, 0x5A, 0x60));
            g.fill(new RoundRectangle2D.Double(87, 126, 17, 42, 17, 17));
            g.rotate(-0.58, 96, 147);
            g.fill(new RoundRectangle2D.Double(82, 123, 17, 38, 17, 17));
            g.rotate(0.58, 96, 147);
            g.setColor(new Color(0xC8, 0x91, 0x72));
            g.fillOval(450, 209, 13, 13);
            g.fillOval(113, 208, 9, 9);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(455, 180, 455, 196);
            g.drawLine(447, 188, 463, 188);

            // Two friendly figures sit behind the laptop.
            g.setColor(new Color(0xF0, 0xE8, 0xE8));
            g.fill(new RoundRectangle2D.Double(123, 274, 79, 104, 30, 30));
            g.setColor(new Color(0xF0, 0xE8, 0xE8));
            g.fill(new RoundRectangle2D.Double(356, 271, 82, 108, 30, 30));

            // Left student's sweater and arms.
            g.setColor(new Color(0xD8, 0x21, 0x28));
            g.fill(new RoundRectangle2D.Double(121, 215, 110, 122, 40, 40));
            g.setStroke(new BasicStroke(20, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(143, 244, 190, 286);
            g.setColor(new Color(0xE7, 0xB1, 0x8B));
            g.fillOval(179, 278, 20, 20);
            g.setColor(new Color(0xF0, 0xC3, 0x9D));
            g.fillRoundRect(157, 178, 67, 71, 29, 29);
            g.setColor(new Color(0x33, 0x35, 0x32));
            Path2D leftHair = new Path2D.Double();
            leftHair.moveTo(155, 209);
            leftHair.curveTo(148, 175, 169, 151, 197, 157);
            leftHair.curveTo(220, 161, 232, 180, 223, 205);
            leftHair.curveTo(212, 192, 201, 183, 185, 180);
            leftHair.curveTo(178, 195, 168, 203, 155, 209);
            leftHair.closePath();
            g.fill(leftHair);
            drawFace(g, 177, 213, new Color(0x3B, 0x39, 0x35));

            // Right student's warm jacket and reaching arm.
            g.setColor(new Color(0xE5, 0x9B, 0x70));
            g.fill(new RoundRectangle2D.Double(328, 213, 108, 126, 40, 40));
            g.setStroke(new BasicStroke(20, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(350, 244, 326, 281);
            g.setColor(new Color(0xB9, 0x76, 0x55));
            g.fillOval(315, 275, 21, 21);
            g.setColor(new Color(0xB9, 0x7A, 0x5D));
            g.fillRoundRect(320, 170, 69, 75, 31, 31);
            g.setColor(new Color(0x2D, 0x2F, 0x2C));
            Path2D rightHair = new Path2D.Double();
            rightHair.moveTo(316, 202);
            rightHair.curveTo(312, 169, 330, 146, 358, 148);
            rightHair.curveTo(390, 148, 402, 173, 388, 207);
            rightHair.curveTo(379, 194, 366, 183, 345, 181);
            rightHair.curveTo(338, 194, 328, 201, 316, 202);
            rightHair.closePath();
            g.fill(rightHair);
            drawFace(g, 342, 207, new Color(0x2D, 0x2F, 0x2C));

            // Laptop: charcoal frame, white screen, and a red MEC dashboard accent.
            g.setColor(new Color(0x3A, 0x3B, 0x40));
            g.fill(new RoundRectangle2D.Double(200, 229, 165, 119, 17, 17));
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(208, 237, 149, 103, 11, 11));
            g.setColor(new Color(0xD8, 0x21, 0x28));
            g.fill(new RoundRectangle2D.Double(222, 250, 28, 6, 5, 5));
            g.setColor(new Color(0xF1, 0xF2, 0xF4));
            g.fill(new RoundRectangle2D.Double(222, 264, 119, 25, 7, 7));
            g.setColor(new Color(0xF8, 0xE4, 0xE5));
            g.fill(new RoundRectangle2D.Double(222, 297, 53, 29, 7, 7));
            g.setColor(new Color(0xE9, 0xEA, 0xED));
            g.fill(new RoundRectangle2D.Double(283, 297, 58, 29, 7, 7));
            g.setColor(new Color(0xB9, 0xBD, 0xC4));
            Path2D laptopBase = new Path2D.Double();
            laptopBase.moveTo(193, 348);
            laptopBase.lineTo(372, 348);
            laptopBase.lineTo(355, 362);
            laptopBase.lineTo(208, 362);
            laptopBase.closePath();
            g.fill(laptopBase);
            g.setColor(new Color(0x8F, 0x93, 0x9A));
            g.fill(new RoundRectangle2D.Double(258, 350, 48, 4, 3, 3));

            // Plant pot and grounding line bring the scene together.
            g.setColor(new Color(0xB9, 0x5A, 0x60));
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(104, 318, 104, 283);
            g.drawLine(104, 301, 88, 286);
            g.drawLine(104, 296, 120, 280);
            g.setColor(new Color(0xD7, 0xBA, 0xB1));
            g.fill(new RoundRectangle2D.Double(89, 316, 31, 30, 7, 7));
        }

        private static void drawFace(Graphics2D g, int centerX, int eyeY, Color detail) {
            g.setColor(detail);
            g.fillOval(centerX - 10, eyeY, 3, 4);
            g.fillOval(centerX + 7, eyeY, 3, 4);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(centerX - 5, eyeY + 10, 12, 8, 205, 130);
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
                UiKit.roundFill(g2, 1, 1, w - 2, h - 2, 12,
                    hasFocus() ? Color.WHITE : LoginPalette.FIELD);
                UiKit.roundStroke(g2, 1, 1, w - 2, h - 2, 12,
                    hasFocus() ? LoginPalette.FIELD_FOCUS : LoginPalette.FIELD_BORDER,
                    hasFocus() ? 1.7f : 1.1f);
                if (hasFocus()) {
                    g2.setColor(new Color(LoginPalette.FIELD_FOCUS.getRed(),
                        LoginPalette.FIELD_FOCUS.getGreen(), LoginPalette.FIELD_FOCUS.getBlue(), 24));
                    g2.fill(new java.awt.geom.RoundRectangle2D.Double(3, 3, w - 6, h - 6,
                        10, 10));
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
                    hintG2.setColor(new Color(0x9A, 0xA1, 0x9C));
                    hintG2.drawString(hint, 15, (getHeight() - metrics.getHeight()) / 2f
                        + metrics.getAscent());
                } finally {
                    hintG2.dispose();
                }
            }
        }
    }

    /** Round icon button that toggles password visibility. */
    private static final class EyeToggle extends UiKit.AnimatedButton {
        private final JPasswordField target;
        private boolean visibleText;

        private EyeToggle(JPasswordField target) {
            super("");
            this.target = target;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setToolTipText("Show password");
            getAccessibleContext().setAccessibleName("Show password");
            setPreferredSize(new Dimension(46, 46));
            setMaximumSize(new Dimension(46, 46));
            addActionListener(event -> {
                visibleText = !visibleText;
                target.setEchoChar(visibleText ? (char) 0 : '\u2022');
                String action = visibleText ? "Hide password" : "Show password";
                setToolTipText(action);
                getAccessibleContext().setAccessibleName(action);
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
                UiKit.roundFill(g2, 0, 0, w, h, h / 2.0,
                    UiKit.blend(Color.WHITE, PortalTheme.ACCENT_SOFT, hoverAmount()));
                (visibleText ? Glyph.EYE_OFF : Glyph.EYE)
                    .paint(g2, (w - 19) / 2.0, (h - 19) / 2.0, 19,
                        UiKit.blend(LoginPalette.MUTED, LoginPalette.RED, hoverAmount()));
                paintFocusRing(g2, w, h, h / 2.0);
            } finally {
                g2.dispose();
            }
        }
    }
}
