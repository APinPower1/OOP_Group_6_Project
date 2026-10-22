package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.LoginRequest;
import edu.mec.studentportal.client.dto.LoginResponse;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
        super(new BorderLayout(12, 12));
        this.api = api;
        this.session = session;

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(new JLabel("Email"), constraints);
        constraints.gridx = 1;
        form.add(emailField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        form.add(new JLabel("Password"), constraints);
        constraints.gridx = 1;
        form.add(passwordField, constraints);
        constraints.gridx = 1;
        constraints.gridy = 2;
        form.add(loginButton, constraints);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(form, BorderLayout.CENTER);
        add(wrapper, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.CENTER);
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
