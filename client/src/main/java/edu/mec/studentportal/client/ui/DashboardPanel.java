package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.SessionContext;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.BorderFactory;

/** Shared chrome for the four role dashboards: title, identity, refresh/logout, tabs, status. */
abstract class DashboardPanel extends JPanel implements MainFrame.Refreshable {
    protected final transient ApiClient api;
    protected final transient SessionContext session;
    protected final JTabbedPane tabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel(" ");

    protected DashboardPanel(String title, ApiClient api, SessionContext session) {
        super(new BorderLayout(0, 18));
        this.api = api;
        this.session = session;
        setBackground(SwingTheme.PAGE);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 18, 28));

        JButton refreshButton = new JButton("Refresh all");
        refreshButton.addActionListener(e -> refreshAll());
        JButton logoutButton = new JButton("Logout");
        logoutButton.setForeground(SwingTheme.TEAL);
        logoutButton.addActionListener(e -> session.clearSession());

        String identity = session.getUser() == null
            ? ""
            : session.getUser().fullName() + "  ·  " + session.getRole();
        JLabel eyebrow = new JLabel("STUDENT PORTAL");
        eyebrow.setForeground(SwingTheme.TEAL);
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 11f));
        JLabel heading = new JLabel(title);
        heading.setForeground(SwingTheme.NAVY);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 25f));
        JLabel identityLabel = new JLabel(identity);
        identityLabel.setForeground(SwingTheme.MUTED);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(logoutButton);

        JPanel titleBlock = new JPanel(new BorderLayout(0, 5));
        titleBlock.setOpaque(false);
        titleBlock.add(eyebrow, BorderLayout.NORTH);
        titleBlock.add(heading, BorderLayout.CENTER);
        titleBlock.add(identityLabel, BorderLayout.SOUTH);

        JPanel north = new JPanel(new BorderLayout(16, 0));
        north.setBackground(SwingTheme.SURFACE);
        north.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingTheme.BORDER),
            BorderFactory.createEmptyBorder(18, 22, 18, 18)
        ));
        north.add(titleBlock, BorderLayout.WEST);
        north.add(actions, BorderLayout.EAST);

        tabs.setBackground(SwingTheme.SURFACE);
        tabs.setForeground(SwingTheme.TEXT);
        tabs.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingTheme.BORDER),
            BorderFactory.createEmptyBorder(6, 8, 8, 8)
        ));

        statusLabel.setForeground(SwingTheme.MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

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
