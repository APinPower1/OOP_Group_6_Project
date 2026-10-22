package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.SessionContext;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/** Shared chrome for the four role dashboards: title, identity, refresh/logout, tabs, status. */
abstract class DashboardPanel extends JPanel implements MainFrame.Refreshable {
    protected final transient ApiClient api;
    protected final transient SessionContext session;
    protected final JTabbedPane tabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel(" ");

    protected DashboardPanel(String title, ApiClient api, SessionContext session) {
        super(new BorderLayout(8, 8));
        this.api = api;
        this.session = session;

        JButton refreshButton = new JButton("Refresh all");
        refreshButton.addActionListener(e -> refreshAll());
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> session.clearSession());

        String identity = session.getUser() == null
            ? ""
            : session.getUser().fullName() + "  ·  " + session.getRole();
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refreshButton);
        actions.add(logoutButton);

        JPanel north = new JPanel(new BorderLayout());
        JPanel titleBlock = new JPanel(new BorderLayout());
        titleBlock.add(new JLabel("<html><b>" + title + "</b></html>"), BorderLayout.NORTH);
        titleBlock.add(new JLabel(identity), BorderLayout.SOUTH);
        north.add(titleBlock, BorderLayout.WEST);
        north.add(actions, BorderLayout.EAST);

        add(north, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    protected void status(String message) {
        statusLabel.setText(message == null || message.isBlank() ? " " : message);
    }

    protected void error(ApiException exception) {
        status(" ");
        UiSupport.showError(this, exception);
    }

    protected void failure(Throwable throwable) {
        status(" ");
        UiSupport.showFailure(this, throwable);
    }
}
