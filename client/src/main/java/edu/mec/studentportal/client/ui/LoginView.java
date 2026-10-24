package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.LoginRequest;
import edu.mec.studentportal.client.dto.LoginResponse;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Email/password sign-in; on success stores the JWT in memory and navigates via the session. */
public final class LoginView extends JPanel {
    private final transient ApiClient api;
    private final transient SessionContext session;

    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final char passwordEchoChar = passwordField.getEchoChar();
    private final JCheckBox showPassword = new JCheckBox("Show password");
    private final JButton loginButton = new JButton("Sign in");
    private final JLabel statusLabel = new JLabel(" ");

    public LoginView(ApiClient api, SessionContext session) {
        super(new BorderLayout());
        this.api = api;
        this.session = session;
        setBackground(SwingTheme.PAGE);

        CampusBanner welcome = new CampusBanner();
        welcome.setPreferredSize(new Dimension(420, 400));
        welcome.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        welcome.setLayout(new GridBagLayout());
        GridBagConstraints welcomeConstraints = new GridBagConstraints();
        welcomeConstraints.gridx = 0;
        welcomeConstraints.anchor = GridBagConstraints.WEST;
        welcomeConstraints.weightx = 1;
        welcomeConstraints.fill = GridBagConstraints.HORIZONTAL;
        welcomeConstraints.insets = new Insets(0, 0, 14, 0);
        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        JPanel monogram = new JPanel(new GridBagLayout());
        monogram.setBackground(SwingTheme.ACCENT);
        monogram.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 200, 112), 2),
            BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
        JLabel monogramText = new JLabel("MEC");
        monogramText.setForeground(Color.WHITE);
        monogramText.setFont(monogramText.getFont().deriveFont(Font.BOLD, 16f));
        monogram.add(monogramText);
        JPanel collegeName = new JPanel(new GridBagLayout());
        collegeName.setOpaque(false);
        GridBagConstraints collegeConstraints = new GridBagConstraints();
        collegeConstraints.gridx = 0;
        collegeConstraints.anchor = GridBagConstraints.WEST;
        collegeConstraints.insets = new Insets(0, 0, 3, 0);
        JLabel college = new JLabel("GOVT. MODEL ENGINEERING COLLEGE");
        college.setForeground(Color.WHITE);
        college.setFont(college.getFont().deriveFont(Font.BOLD, 12f));
        collegeConstraints.gridy = 0;
        collegeName.add(college, collegeConstraints);
        JLabel location = new JLabel("THRIKKAKARA  ·  KOCHI");
        location.setForeground(SwingTheme.GOLD);
        location.setFont(location.getFont().deriveFont(Font.BOLD, 10f));
        collegeConstraints.gridy = 1;
        collegeConstraints.insets = new Insets(0, 0, 0, 0);
        collegeName.add(location, collegeConstraints);
        brand.add(monogram, BorderLayout.WEST);
        brand.add(collegeName, BorderLayout.CENTER);
        welcomeConstraints.gridy = 0;
        welcome.add(brand, welcomeConstraints);
        JLabel welcomeTitle = new JLabel(
            "<html>Ideas start here.<br>Your community<br>starts at MEC.</html>"
        );
        welcomeTitle.setForeground(Color.WHITE);
        welcomeTitle.setFont(welcomeTitle.getFont().deriveFont(Font.BOLD, 29f));
        welcomeConstraints.gridy = 1;
        welcomeConstraints.insets = new Insets(4, 0, 16, 0);
        welcome.add(welcomeTitle, welcomeConstraints);
        JLabel welcomeCopy = new JLabel(
            "<html>Discover campus events, communities<br>"
                + "and opportunities to get involved.</html>"
        );
        welcomeCopy.setForeground(new Color(231, 230, 240));
        welcomeCopy.setFont(welcomeCopy.getFont().deriveFont(14f));
        welcomeConstraints.gridy = 2;
        welcomeConstraints.insets = new Insets(0, 0, 0, 0);
        welcome.add(welcomeCopy, welcomeConstraints);
        JLabel highlights = new JLabel("EVENTS     ·     CLUBS     ·     CAMPUS LIFE");
        highlights.setForeground(SwingTheme.GOLD);
        highlights.setFont(highlights.getFont().deriveFont(Font.BOLD, 10f));
        welcomeConstraints.gridy = 3;
        welcomeConstraints.insets = new Insets(26, 0, 0, 0);
        welcome.add(highlights, welcomeConstraints);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(SwingTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(34, 42, 30, 42));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 0, 6, 0);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        JLabel portalLabel = new JLabel("MEC STUDENT PORTAL");
        portalLabel.setForeground(SwingTheme.ACCENT);
        portalLabel.setFont(portalLabel.getFont().deriveFont(Font.BOLD, 11f));
        constraints.gridy = 0;
        form.add(portalLabel, constraints);
        constraints.gridy = 1;
        JLabel formTitle = new JLabel("Welcome back");
        formTitle.setForeground(SwingTheme.NAVY);
        formTitle.setFont(formTitle.getFont().deriveFont(Font.BOLD, 28f));
        form.add(formTitle, constraints);
        constraints.gridy = 2;
        JLabel subtitle = new JLabel("Sign in with your college account to continue.");
        subtitle.setForeground(SwingTheme.MUTED);
        form.add(subtitle, constraints);
        constraints.gridy = 3;
        constraints.insets = new Insets(25, 0, 6, 0);
        JLabel emailLabel = fieldLabel("EMAIL ADDRESS");
        emailLabel.setLabelFor(emailField);
        form.add(emailLabel, constraints);
        constraints.gridy = 4;
        constraints.insets = new Insets(0, 0, 12, 0);
        emailField.setColumns(28);
        emailField.setName("login.email");
        emailField.setToolTipText("Enter your college email address");
        emailField.setPreferredSize(new Dimension(320, 42));
        form.add(emailField, constraints);
        constraints.gridy = 5;
        constraints.insets = new Insets(8, 0, 5, 0);
        JLabel passwordLabel = fieldLabel("PASSWORD");
        passwordLabel.setLabelFor(passwordField);
        form.add(passwordLabel, constraints);
        constraints.gridy = 6;
        constraints.insets = new Insets(0, 0, 20, 0);
        passwordField.setColumns(28);
        passwordField.setName("login.password");
        passwordField.setPreferredSize(new Dimension(320, 42));
        form.add(passwordField, constraints);
        constraints.gridy = 7;
        constraints.insets = new Insets(0, 0, 14, 0);
        showPassword.setName("login.showPassword");
        showPassword.setOpaque(false);
        showPassword.addActionListener(e -> passwordField.setEchoChar(
            showPassword.isSelected() ? (char) 0 : passwordEchoChar
        ));
        form.add(showPassword, constraints);
        constraints.gridy = 8;
        constraints.insets = new Insets(0, 0, 10, 0);
        loginButton.setBackground(SwingTheme.ACCENT);
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setDefaultCapable(true);
        loginButton.setToolTipText("Sign in with your campus account");
        loginButton.setPreferredSize(new Dimension(320, 46));
        form.add(loginButton, constraints);

        JLabel secureNote = new JLabel("Secure access  ·  Your session stays private");
        secureNote.setForeground(SwingTheme.MUTED);
        secureNote.setHorizontalAlignment(JLabel.CENTER);
        constraints.gridy = 9;
        constraints.insets = new Insets(10, 0, 0, 0);
        form.add(secureNote, constraints);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(SwingTheme.SURFACE);
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingTheme.BORDER),
            BorderFactory.createEmptyBorder(1, 1, 1, 1)
        ));
        content.add(welcome, BorderLayout.WEST);
        content.add(form, BorderLayout.CENTER);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(SwingTheme.PAGE);
        outer.setBorder(BorderFactory.createEmptyBorder(48, 48, 48, 48));
        outer.add(content, BorderLayout.CENTER);
        add(outer, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setBackground(SwingTheme.PAGE);
        statusLabel.setForeground(SwingTheme.MUTED);
        south.add(statusLabel);
        add(south, BorderLayout.SOUTH);

        loginButton.addActionListener(e -> signIn());
        emailField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> signIn());
        javax.swing.KeyStroke enter = javax.swing.KeyStroke.getKeyStroke("ENTER");
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter, "login");
        getActionMap().put("login", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                signIn();
            }
        });
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(SwingTheme.MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private void signIn() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (email.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Enter your email and password.");
            return;
        }
        statusLabel.setText("Signing in…");
        loginButton.setText("Signing in…");
        ApiTask.run(
            () -> api.post("/auth/login", new LoginRequest(email, password), LoginResponse.class),
            response -> {
                statusLabel.setText(" ");
                loginButton.setText("Sign in");
                passwordField.setText("");
                session.setSession(response.accessToken(), response.user().toUserSummary());
            },
            error -> {
                statusLabel.setText(" ");
                loginButton.setText("Sign in");
                UiSupport.showError(this, error);
            },
            loginButton
        );
    }

    public void showStatus(String message) {
        statusLabel.setText(message == null || message.isBlank() ? " " : message);
        SwingUtilities.invokeLater(emailField::requestFocusInWindow);
    }

    public void clearInputs() {
        emailField.setText("");
        passwordField.setText("");
    }

    JButton getLoginButton() {
        return loginButton;
    }
}
