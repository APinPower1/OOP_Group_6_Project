package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.LoginRequest;
import edu.mec.studentportal.client.dto.LoginResponse;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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
        JLabel brand = new JLabel("CAMPUS CONNECT  /  STUDENT LIFE");
        brand.setForeground(new Color(147, 218, 208));
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 12f));
        welcomeConstraints.gridy = 0;
        welcome.add(brand, welcomeConstraints);
        JLabel welcomeTitle = new JLabel(
            "<html>Your campus.<br>Your people.<br>Your moment.</html>"
        );
        welcomeTitle.setForeground(Color.WHITE);
        welcomeTitle.setFont(welcomeTitle.getFont().deriveFont(Font.BOLD, 30f));
        welcomeConstraints.gridy = 1;
        welcomeConstraints.insets = new Insets(0, 0, 18, 0);
        welcome.add(welcomeTitle, welcomeConstraints);
        JLabel welcomeCopy = new JLabel(
            "<html>Find the events, ideas and communities<br>"
                + "that make college feel like yours.</html>"
        );
        welcomeCopy.setForeground(new Color(202, 213, 224));
        welcomeCopy.setFont(welcomeCopy.getFont().deriveFont(15f));
        welcomeConstraints.gridy = 2;
        welcomeConstraints.insets = new Insets(0, 0, 0, 0);
        welcome.add(welcomeCopy, welcomeConstraints);
        JLabel highlights = new JLabel("EVENTS     ·     CLUBS     ·     COMMUNITY");
        highlights.setForeground(new Color(173, 219, 212));
        highlights.setFont(highlights.getFont().deriveFont(Font.BOLD, 10f));
        welcomeConstraints.gridy = 3;
        welcomeConstraints.insets = new Insets(30, 0, 0, 0);
        welcome.add(highlights, welcomeConstraints);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(SwingTheme.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(36, 40, 32, 40));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 0, 7, 0);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        JLabel formTitle = new JLabel("Welcome back");
        formTitle.setForeground(SwingTheme.NAVY);
        formTitle.setFont(formTitle.getFont().deriveFont(Font.BOLD, 27f));
        form.add(formTitle, constraints);
        constraints.gridy = 1;
        JLabel subtitle = new JLabel("Sign in with your campus account to continue.");
        subtitle.setForeground(SwingTheme.MUTED);
        form.add(subtitle, constraints);
        constraints.gridy = 2;
        constraints.insets = new Insets(24, 0, 5, 0);
        form.add(fieldLabel("EMAIL ADDRESS"), constraints);
        constraints.gridy = 3;
        constraints.insets = new Insets(0, 0, 12, 0);
        emailField.setColumns(28);
        emailField.setPreferredSize(new Dimension(320, 42));
        form.add(emailField, constraints);
        constraints.gridy = 4;
        constraints.insets = new Insets(8, 0, 5, 0);
        form.add(fieldLabel("PASSWORD"), constraints);
        constraints.gridy = 5;
        constraints.insets = new Insets(0, 0, 20, 0);
        passwordField.setColumns(28);
        passwordField.setPreferredSize(new Dimension(320, 42));
        form.add(passwordField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.insets = new Insets(0, 0, 10, 0);
        loginButton.setBackground(SwingTheme.TEAL);
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setPreferredSize(new Dimension(320, 44));
        form.add(loginButton, constraints);

        JLabel secureNote = new JLabel("Secure access to your campus community");
        secureNote.setForeground(SwingTheme.MUTED);
        secureNote.setHorizontalAlignment(JLabel.CENTER);
        constraints.gridy = 7;
        constraints.insets = new Insets(10, 0, 0, 0);
        form.add(secureNote, constraints);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(SwingTheme.SURFACE);
        content.setBorder(BorderFactory.createLineBorder(SwingTheme.BORDER));
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
        ApiTask.run(
            () -> api.post("/auth/login", new LoginRequest(email, password), LoginResponse.class),
            response -> {
                statusLabel.setText(" ");
                passwordField.setText("");
                session.setSession(response.accessToken(), response.user().toUserSummary());
            },
            error -> {
                statusLabel.setText(" ");
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
}
