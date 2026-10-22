package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.SessionContext;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
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
        JLabel eyebrow = new JLabel("CAMPUS CONNECT  /  " + session.getRole());
        eyebrow.setForeground(new java.awt.Color(176, 223, 215));
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 11f));
        JLabel heading = new JLabel(title);
        heading.setForeground(java.awt.Color.WHITE);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 27f));
        JLabel identityLabel = new JLabel(identity);
        identityLabel.setForeground(new java.awt.Color(211, 222, 231));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        logoutButton.setBackground(new java.awt.Color(235, 244, 242));
        actions.add(refreshButton);
        actions.add(logoutButton);

        JPanel titleBlock = new JPanel(new BorderLayout(0, 5));
        titleBlock.setOpaque(false);
        titleBlock.add(eyebrow, BorderLayout.NORTH);
        titleBlock.add(heading, BorderLayout.CENTER);
        titleBlock.add(identityLabel, BorderLayout.SOUTH);

        CampusBanner north = new CampusBanner();
        north.setLayout(new BorderLayout(16, 0));
        north.setPreferredSize(new java.awt.Dimension(600, 142));
        north.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 22));
        north.add(titleBlock, BorderLayout.WEST);
        north.add(actions, BorderLayout.EAST);

        tabs.setBackground(SwingTheme.SURFACE);
        tabs.setForeground(SwingTheme.TEXT);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 13f));
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SwingTheme.BORDER),
            BorderFactory.createEmptyBorder(8, 10, 10, 10)
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
