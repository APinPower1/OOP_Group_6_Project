package edu.mec.studentportal.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.auth.SessionContext;
import edu.mec.studentportal.client.auth.SessionContext.UserSummary;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

public final class PortalFrame extends JFrame {
    private static final String LOGIN = "login";
    private static final String WORKSPACE = "workspace";

    private final ApiClient apiClient;
    private final SessionContext session;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JTextField emailField = new JTextField(28);
    private final JPasswordField passwordField = new JPasswordField(28);
    private final JLabel loginStatus = new JLabel(" ");
    private final JButton loginButton = new JButton("Sign in");

    public PortalFrame(ApiClient apiClient, SessionContext session) {
        super("MEC Student Portal");
        this.apiClient = apiClient;
        this.session = session;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 700));
        setSize(1280, 820);
        setLocationRelativeTo(null);
        cards.add(createLoginScreen(), LOGIN);
        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, LOGIN);
    }

    void showWorkspace() {
        cards.add(new WorkspacePanel(apiClient, session, this::signOut, this::sessionExpired), WORKSPACE);
        cardLayout.show(cards, WORKSPACE);
        revalidate();
        repaint();
    }

    void sessionExpired() {
        signOut();
        javax.swing.JOptionPane.showMessageDialog(
            this,
            "Your session has expired. Please sign in again.",
            "Session expired",
            javax.swing.JOptionPane.INFORMATION_MESSAGE
        );
    }

    private JPanel createLoginScreen() {
        JPanel screen = new JPanel(new GridBagLayout());
        screen.setBackground(PortalTheme.CANVAS);
        JPanel card = new JPanel(new BorderLayout(0, 22));
        card.setBackground(PortalTheme.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PortalTheme.BORDER),
            BorderFactory.createEmptyBorder(40, 46, 38, 46)
        ));

        JPanel heading = new JPanel(new BorderLayout(0, 7));
        heading.setOpaque(false);
        JLabel eyebrow = new JLabel("MEC STUDENT PORTAL");
        eyebrow.setForeground(PortalTheme.BLUE);
        eyebrow.setFont(PortalTheme.LABEL);
        JLabel title = new JLabel("Welcome back");
        title.setForeground(PortalTheme.INK);
        title.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 28));
        JLabel subtitle = new JLabel("Sign in to manage campus life in one place.");
        subtitle.setForeground(PortalTheme.MUTED);
        subtitle.setFont(PortalTheme.BODY);
        JPanel titleStack = new JPanel(new BorderLayout(0, 8));
        titleStack.setOpaque(false);
        titleStack.add(title, BorderLayout.NORTH);
        titleStack.add(subtitle, BorderLayout.CENTER);
        heading.add(eyebrow, BorderLayout.NORTH);
        heading.add(titleStack, BorderLayout.CENTER);
        card.add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(0, 0, 7, 0);
        addFormLabel(form, "University email", constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 18, 0);
        styleInput(emailField);
        form.add(emailField, constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 7, 0);
        addFormLabel(form, "Password", constraints);
        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 13, 0);
        styleInput(passwordField);
        form.add(passwordField, constraints);
        constraints.gridy++;
        loginStatus.setForeground(PortalTheme.RED);
        form.add(loginStatus, constraints);
        constraints.gridy++;
        constraints.insets = new Insets(10, 0, 0, 0);
        loginButton.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 14));
        loginButton.setForeground(Color.WHITE);
        loginButton.setBackground(PortalTheme.BLUE);
        loginButton.setFocusPainted(false);
        loginButton.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        loginButton.addActionListener(event -> signIn());
        form.add(loginButton, constraints);
        passwordField.addActionListener(event -> signIn());
        card.add(form, BorderLayout.CENTER);

        JLabel footer = new JLabel("Secure access provided by your campus account");
        footer.setForeground(PortalTheme.MUTED);
        footer.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        card.add(footer, BorderLayout.SOUTH);
        GridBagConstraints position = new GridBagConstraints();
        position.gridx = 0;
        position.gridy = 0;
        position.insets = new Insets(20, 20, 20, 20);
        screen.add(card, position);
        return screen;
    }

    private void signIn() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (email.isEmpty() || password.isEmpty()) {
            loginStatus.setText("Enter your university email and password.");
            return;
        }
        loginButton.setEnabled(false);
        loginStatus.setForeground(PortalTheme.MUTED);
        loginStatus.setText("Signing in...");
        new SwingWorker<JsonNode, Void>() {
            @Override
            protected JsonNode doInBackground() {
                return apiClient.post("auth/login", Map.of("email", email, "password", password));
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                passwordField.setText("");
                try {
                    JsonNode response = get();
                    JsonNode user = response.path("user");
                    session.setSession(
                        response.path("accessToken").asText(),
                        new UserSummary(
                            user.path("id").asLong(),
                            user.path("fullName").asText(),
                            user.path("email").asText(),
                            user.path("role").asText()
                        )
                    );
                    showWorkspace();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof ApiException apiException) {
                        loginStatus.setForeground(PortalTheme.RED);
                        loginStatus.setText(apiException.getMessage());
                    } else {
                        loginStatus.setForeground(PortalTheme.RED);
                        loginStatus.setText("Sign in failed. Please try again.");
                    }
                }
            }
        }.execute();
    }

    private void signOut() {
        session.clearSession();
        cards.removeAll();
        cards.add(createLoginScreen(), LOGIN);
        cardLayout.show(cards, LOGIN);
        revalidate();
        repaint();
    }

    private static void addFormLabel(JPanel form, String text, GridBagConstraints constraints) {
        JLabel label = new JLabel(text);
        label.setForeground(PortalTheme.INK);
        label.setFont(PortalTheme.LABEL);
        form.add(label, constraints);
    }

    private static void styleInput(JTextField field) {
        field.setFont(PortalTheme.BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PortalTheme.BORDER),
            BorderFactory.createEmptyBorder(9, 10, 9, 10)
        ));
        field.setPreferredSize(new Dimension(330, 42));
    }
}
