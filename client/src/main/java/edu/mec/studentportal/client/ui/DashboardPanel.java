package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.UserSummary;
import edu.mec.studentportal.client.ui.theme.Glyph;
import edu.mec.studentportal.client.ui.theme.PortalTheme;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Shared chrome for the four role dashboards: a focused workspace header, bright navigation
 * rail, one content card per tab — including the shared event calendar every role lands on —
 * and a slim status footer.
 */
abstract class DashboardPanel extends JPanel implements MainFrame.Refreshable {
    protected final transient ApiClient api;
    protected final transient SessionContext session;
    protected final SidebarTabs tabs;
    private final JLabel statusLabel = new JLabel(" ");

    protected DashboardPanel(String title, ApiClient api, SessionContext session) {
        super(new BorderLayout());
        this.api = api;
        this.session = session;
        setBackground(PortalTheme.CANVAS);

        tabs = new SidebarTabs(session);
        tabs.addTab("Calendar", new EventCalendarPanel(api, session));

        JPanel topbar = new JPanel(new BorderLayout(24, 0));
        topbar.setBackground(Color.WHITE);
        topbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, PortalTheme.HAIRLINE),
            new EmptyBorder(17, 28, 17, 28)));

        topbar.add(workspaceHeading(title), BorderLayout.WEST);

        JButton refreshButton = UiKit.ghostButton("Refresh all", Glyph.REFRESH);
        refreshButton.addActionListener(event -> refreshAll());
        JButton profileButton = UiKit.ghostButton("Account", Glyph.USERS);
        profileButton.addActionListener(event ->
            ProfileDialog.showProfile(this, api, session));
        JButton logoutButton = UiKit.darkButton("Log out", Glyph.LOGOUT);
        logoutButton.addActionListener(event -> session.clearSession());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(profileButton);
        actions.add(identityChip());
        actions.add(logoutButton);
        topbar.add(actions, BorderLayout.EAST);

        statusLabel.setFont(PortalTheme.SM);
        statusLabel.setForeground(PortalTheme.MUTED);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Color.WHITE);
        statusLabel.setBorder(new EmptyBorder(8, 28, 10, 28));

        add(topbar, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JComponent workspaceHeading(String title) {
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new javax.swing.BoxLayout(heading, javax.swing.BoxLayout.Y_AXIS));

        String fullName = "";
        try {
            UserSummary user = session == null ? null : session.getUser();
            fullName = user == null ? "" : user.fullName();
        } catch (RuntimeException ignored) {
            // The dashboard remains useful even when identity data is temporarily unavailable.
        }
        String firstName = fullName.isBlank() ? "there" : fullName.trim().split("\\s+")[0];
        int hour = java.time.LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon"
            : "Good evening";

        JLabel context = new JLabel(LocalDate.now().format(
            DateTimeFormatter.ofPattern("EEEE, dd MMMM")).toUpperCase());
        context.setFont(PortalTheme.EYEBROW);
        context.setForeground(PortalTheme.ACCENT);
        JLabel welcome = new JLabel(greeting + ", " + firstName);
        welcome.setFont(PortalTheme.XL_BOLD);
        welcome.setForeground(PortalTheme.INK);
        JLabel detail = new JLabel(title + "  ·  Campus activity at a glance");
        detail.setFont(PortalTheme.SM);
        detail.setForeground(PortalTheme.MUTED);
        context.setAlignmentX(0f);
        welcome.setAlignmentX(0f);
        detail.setAlignmentX(0f);
        heading.add(context);
        heading.add(welcome);
        heading.add(detail);
        return heading;
    }

    /** Avatar + name + role shown at the right edge of the topbar. */
    private JComponent identityChip() {
        UserSummary user = null;
        try {
            user = session == null ? null : session.getUser();
        } catch (RuntimeException ignored) {
            user = null;
        }
        String fullName = user == null ? "" : user.fullName();
        String role = "";
        try {
            if (session != null && session.isLoggedIn() && session.getRole() != null) {
                role = session.getRole().name();
            }
        } catch (RuntimeException ignored) {
            role = "";
        }

        JPanel chip = new JPanel(new BorderLayout(10, 0));
        chip.setOpaque(false);
        chip.setBorder(new EmptyBorder(0, 0, 0, 2));
        chip.add(UiKit.avatar(fullName.isEmpty() ? "?" : fullName, 34), BorderLayout.WEST);

        JPanel texts = new JPanel(new BorderLayout(0, 1));
        texts.setOpaque(false);
        JLabel name = new JLabel(fullName.isEmpty() ? "Signed out" : fullName);
        name.setFont(PortalTheme.SM_BOLD);
        name.setForeground(PortalTheme.INK);
        JLabel roleLabel = new JLabel(role.isEmpty() ? " " : role);
        roleLabel.setFont(PortalTheme.XS);
        roleLabel.setForeground(PortalTheme.MUTED);
        texts.add(name, BorderLayout.NORTH);
        texts.add(roleLabel, BorderLayout.SOUTH);
        chip.add(texts, BorderLayout.CENTER);

        JPanel wrap = new JPanel(new BorderLayout(14, 0));
        wrap.setOpaque(false);
        wrap.add(paddedDivider(), BorderLayout.WEST);
        wrap.add(chip, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(240, 48));
        return wrap;
    }

    private static JComponent paddedDivider() {
        JComponent divider = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                graphics.setColor(PortalTheme.HAIRLINE);
                graphics.drawLine(getWidth() / 2, 2, getWidth() / 2, getHeight() - 2);
            }
        };
        divider.setPreferredSize(new Dimension(9, 34));
        divider.setMaximumSize(new Dimension(9, 34));
        return divider;
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
