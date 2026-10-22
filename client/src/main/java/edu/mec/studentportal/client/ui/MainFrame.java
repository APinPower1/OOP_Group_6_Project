package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ClientConfig;
import edu.mec.studentportal.client.api.HttpApiClient;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.Role;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Top-level window: holds the login card and one dashboard card per signed-in role. Reacts to
 * session changes — login shows the role dashboard, logout/401 returns to login.
 */
public final class MainFrame extends JFrame {
    private final transient SessionContext session;
    private final transient ApiClient api;
    private final transient LoginView loginView;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    public MainFrame(ClientConfig config) {
        super("Student Portal with Event Management System");
        this.session = new SessionContext();
        this.api = new HttpApiClient(config, session);
        this.loginView = new LoginView(api, session);
        getRootPane().setDefaultButton(loginView.getLoginButton());

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 680));

        cardPanel.add(loginView, "login");
        add(cardPanel, BorderLayout.CENTER);

        session.addListener(changed -> SwingUtilities.invokeLater(() -> {
            if (changed.isLoggedIn()) {
                showDashboard(changed.getRole());
            } else {
                showLogin();
            }
        }));

        cards.show(cardPanel, "login");
        setTitle("Student Portal — signed out");
    }

    public SessionContext getSession() {
        return session;
    }

    private void showDashboard(Role role) {
        getRootPane().setDefaultButton(null);
        String key = role.name();
        for (java.awt.Component component : cardPanel.getComponents()) {
            if (component.getName() != null && component.getName().equals(key)) {
                cardPanel.remove(component);
                break;
            }
        }
        JPanel dashboard = RoleNavigation.createDashboard(role, api, session);
        dashboard.setName(key);
        cardPanel.add(dashboard, key);
        cardPanel.revalidate();
        cards.show(cardPanel, key);
        setTitle("Student Portal — " + session.getUser().fullName() + " (" + role + ")");
        if (dashboard instanceof Refreshable refreshable) {
            refreshable.refreshAll();
        }
    }

    private void showLogin() {
        getRootPane().setDefaultButton(loginView.getLoginButton());
        for (java.awt.Component component : cardPanel.getComponents()) {
            if (component != loginView) {
                cardPanel.remove(component);
            }
        }
        cardPanel.revalidate();
        cards.show(cardPanel, "login");
        loginView.clearInputs();
        loginView.showStatus("Signed out.");
        setTitle("Student Portal — signed out");
    }

    /** Implemented by dashboards so the frame can trigger an initial data load. */
    public interface Refreshable {
        void refreshAll();
    }
}
