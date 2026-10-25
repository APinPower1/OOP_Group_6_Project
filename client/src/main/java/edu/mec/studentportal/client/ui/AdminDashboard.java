package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.ui.theme.UiKit;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.AdminUserCreateRequest;
import edu.mec.studentportal.client.dto.AdminUserDto;
import edu.mec.studentportal.client.dto.AdminUserPageDto;
import edu.mec.studentportal.client.dto.AdminResetPasswordRequest;
import edu.mec.studentportal.client.dto.AdminUserUpdateRequest;
import edu.mec.studentportal.client.dto.AnnouncementDto;
import edu.mec.studentportal.client.dto.AnnouncementRequest;
import edu.mec.studentportal.client.dto.ClubDto;
import edu.mec.studentportal.client.dto.ClubMemberDto;
import edu.mec.studentportal.client.dto.ClubRequest;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.EventRequest;
import edu.mec.studentportal.client.dto.ParticipationReportDto;
import edu.mec.studentportal.client.dto.RejectRequest;
import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.VenueCreateRequest;
import edu.mec.studentportal.client.dto.VenueResponse;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/**
 * Admin dashboard: user/club/announcement management, event moderation and the
 * participation report.
 */
public final class AdminDashboard extends DashboardPanel {
    private static final TypeReference<List<EventDto>> EVENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ClubDto>> CLUB_LIST = new TypeReference<>() {};
    private static final TypeReference<List<AnnouncementDto>> ANNOUNCEMENT_LIST =
        new TypeReference<>() {};
    private static final TypeReference<List<ClubMemberDto>> MEMBER_LIST = new TypeReference<>() {};
    private static final TypeReference<List<VenueResponse>> VENUE_LIST = new TypeReference<>() {};

    private final DefaultTableModel usersModel = UiSupport.tableModel(
        "ID", "Name", "Email", "Department", "Role", "Enabled", "Password hash"
    );
    private final JTable usersTable = new JTable(usersModel);
    private final JComboBox<String> userRoleFilter = new JComboBox<>(
        new String[] { "ALL", "STUDENT", "ORGANIZER", "FACULTY", "ADMIN" });
    private final JComboBox<String> userEnabledFilter = new JComboBox<>(
        new String[] { "ALL", "Enabled", "Disabled" });
    private final JTextFieldWithAction userSearch = new JTextFieldWithAction();
    private final JSpinner userPage = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
    private AdminUserPageDto userPageResult;

    private final DefaultTableModel moderationModel = UiSupport.tableModel(
        "ID", "Title", "Venue", "Starts", "Ends", "Cap", "Status", "Reason");
    private final JTable moderationTable = new JTable(moderationModel);
    private final JComboBox<String> moderationFilter = new JComboBox<>(
        new String[] { "PENDING_APPROVAL", "ALL", "APPROVED", "REJECTED", "CANCELLED" });
    private List<EventDto> moderationRows = List.of();

    private final DefaultTableModel clubsModel = UiSupport.tableModel(
        "ID", "Name", "Category", "Active", "Members");
    private final JTable clubsTable = new JTable(clubsModel);
    private List<ClubDto> clubRows = List.of();

    private final DefaultTableModel announcementsModel = UiSupport.tableModel(
        "ID", "Title", "Audience", "Status", "Created");
    private final JTable announcementsTable = new JTable(announcementsModel);
    private List<AnnouncementDto> announcementRows = List.of();

    private final DefaultTableModel venuesModel = UiSupport.tableModel(
        "ID", "Name");
    private final JTable venuesTable = new JTable(venuesModel);
    private final javax.swing.JTextField venueNameField = new javax.swing.JTextField(22);

    private final JLabel reportSummary = new JLabel(" ");

    public AdminDashboard(ApiClient api, SessionContext session) {
        super("Admin Dashboard", api, session);
        tabs.addTab("Users", buildUsersTab());
        tabs.addTab("Event moderation", buildModerationTab());
        tabs.addTab("Clubs", buildClubsTab());
        tabs.addTab("Announcements", buildAnnouncementsTab());
        tabs.addTab("Venues", buildVenuesTab());
        tabs.addTab("Reports", buildReportsTab());
    }

    @Override
    public void refreshAll() {
        refreshUsers();
        refreshModeration();
        refreshClubs();
        refreshAnnouncements();
        refreshVenues();
        refreshReport();
    }

    private JPanel buildUsersTab() {
        JButton apply = UiKit.ghostButton("Apply filters", edu.mec.studentportal.client.ui.theme.Glyph.SEARCH);
        apply.addActionListener(e -> refreshUsers());
        userSearch.field().addActionListener(e -> refreshUsers());
        styleFilterCombo(userRoleFilter, 132);
        styleFilterCombo(userEnabledFilter, 132);
        styleFilterSpinner(userPage);
        apply.setPreferredSize(new Dimension(128, 42));

        JPanel filters = UiKit.toolbarSurface(new FlowLayout(FlowLayout.LEFT, 12, 0),
            12, 14, 12, 14);
        filters.add(filterControl("Role", userRoleFilter, 112));
        filters.add(filterControl("Status", userEnabledFilter, 118));
        filters.add(filterControl("Search users", userSearch.field(), 210));
        filters.add(filterControl("Page", userPage, 78));
        filters.add(apply);
        userRoleFilter.addActionListener(e -> refreshUsers());
        userEnabledFilter.addActionListener(e -> refreshUsers());

        JButton create = UiKit.gradientButton("Create user", edu.mec.studentportal.client.ui.theme.Glyph.PLUS);
        create.addActionListener(e -> createUser(create));
        JButton edit = UiKit.ghostButton("Edit user");
        edit.addActionListener(e -> editUser(edit));
        JButton enable = UiKit.ghostButton("Enable");
        enable.addActionListener(e -> setUserEnabled(true, enable));
        JButton disable = UiKit.ghostButton("Disable");
        disable.addActionListener(e -> setUserEnabled(false, disable));
        JButton reset = UiKit.ghostButton("Reset password…");
        reset.addActionListener(e -> resetSelectedPassword(reset));
        JButton next = UiKit.ghostButton("Next page");
        next.addActionListener(e -> {
            if (userPageResult != null && userPageResult.page() + 1 < userPageResult.totalPages()) {
                userPage.setValue(userPageResult.page() + 1);
            }
            refreshUsers();
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 4, 2, 4));
        actions.add(create);
        actions.add(edit);
        actions.add(enable);
        actions.add(disable);
        actions.add(reset);
        actions.add(next);

        JPanel panel = new JPanel(new BorderLayout());
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        filters.setAlignmentX(LEFT_ALIGNMENT);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        top.add(filters);
        top.add(actions);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildModerationTab() {
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshModeration());
        moderationFilter.addActionListener(e -> refreshModeration());
        JButton approve = UiKit.ghostButton("Approve");
        approve.addActionListener(e -> moderate(approve, true));
        JButton reject = UiKit.ghostButton("Reject…");
        reject.addActionListener(e -> moderate(reject, false));
        JButton cancel = UiKit.ghostButton("Cancel event");
        cancel.addActionListener(e -> cancelModerationEvent(cancel));
        JButton details = UiKit.ghostButton("Details");
        details.addActionListener(e -> moderationDetails());

        JPanel buttons = actionBar();
        buttons.add(new JLabel("Status:"));
        buttons.add(moderationFilter);
        buttons.add(refresh);
        buttons.add(details);
        buttons.add(approve);
        buttons.add(reject);
        buttons.add(cancel);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(moderationTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildClubsTab() {
        JButton create = UiKit.ghostButton("Create club");
        create.addActionListener(e -> clubForm(create, null));
        JButton edit = UiKit.ghostButton("Edit club");
        edit.addActionListener(e -> clubForm(edit, selectedClub()));
        JButton deactivate = UiKit.ghostButton("Deactivate");
        deactivate.addActionListener(e -> deactivateClub(deactivate));
        JButton members = UiKit.ghostButton("Members");
        members.addActionListener(e -> showMembers(members));
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshClubs());

        JPanel buttons = actionBar();
        buttons.add(create);
        buttons.add(edit);
        buttons.add(deactivate);
        buttons.add(members);
        buttons.add(refresh);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(clubsTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAnnouncementsTab() {
        JButton post = UiKit.ghostButton("Post announcement");
        post.addActionListener(e -> postAnnouncement(post));
        JButton archive = UiKit.ghostButton("Archive");
        archive.addActionListener(e -> archiveAnnouncement(archive));
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshAnnouncements());

        JPanel buttons = actionBar();
        buttons.add(post);
        buttons.add(archive);
        buttons.add(refresh);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        JScrollPane annScroll = new JScrollPane(announcementsTable);
        panel.add(annScroll, BorderLayout.CENTER);
        announcementsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showAnnouncementDetails();
                }
            }
        });
        return panel;
    }

    private JPanel buildReportsTab() {
        JButton refresh = UiKit.ghostButton("Refresh report");
        refresh.addActionListener(e -> refreshReport());

        JTable table = new JTable(UiSupport.tableModel(
            "Event ID", "Title", "Status", "Capacity", "Confirmed", "Cancelled",
            "Feedback", "Avg rating"));
        table.putClientProperty("terminateEditOnKeyStroke", Boolean.TRUE);
        JPanel panel = new JPanel(new BorderLayout());
        JPanel reportToolbar = UiKit.toolbarSurface(new BorderLayout(0, 8), 12, 14, 12, 14);
        reportToolbar.add(refresh, BorderLayout.NORTH);
        reportToolbar.add(reportSummary, BorderLayout.CENTER);
        panel.add(reportToolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        reportTable = table;
        return panel;
    }

    private JTable reportTable;

    private void refreshReport() {
        status("Loading participation report…");
        ApiTask.run(
            () -> api.get("/admin/reports/participation", ParticipationReportDto.class),
            report -> {
                if (report == null) {
                    status("No report data.");
                    return;
                }
                ParticipationReportDto.EventCounts events = report.events();
                ParticipationReportDto.RegistrationCounts registrations = report.registrations();
                ParticipationReportDto.FeedbackSummary feedback = report.feedback();
                reportSummary.setText("<html><b>Events</b> total " + events.total()
                    + " · pending " + events.pendingApproval()
                    + " · approved " + events.approved()
                    + " · rejected " + events.rejected()
                    + " · cancelled " + events.cancelled()
                    + " &nbsp; | &nbsp; <b>Registrations</b> total " + registrations.total()
                    + " · confirmed " + registrations.confirmed()
                    + " · cancelled " + registrations.cancelled()
                    + " &nbsp; | &nbsp; <b>Feedback</b> " + feedback.count()
                    + " · avg " + (feedback.averageRating() == null
                        ? "n/a" : feedback.averageRating()) + "</html>");
                DefaultTableModel model = (DefaultTableModel) reportTable.getModel();
                UiSupport.clear(model);
                for (ParticipationReportDto.ParticipationRow row : report.participation()) {
                    UiSupport.addRow(
                        model,
                        row.eventId(),
                        row.title(),
                        row.status(),
                        row.capacity(),
                        row.confirmedCount(),
                        row.cancelledCount(),
                        row.feedbackCount(),
                        row.averageRating() == null ? "-" : row.averageRating()
                    );
                }
                status("Participation rows: " + report.participation().size());
            },
            this::error
        );
    }

    private JPanel buildVenuesTab() {
        edu.mec.studentportal.client.ui.theme.UiKit.enableTextEditing(venueNameField);
        JButton add = UiKit.ghostButton("Add venue");
        add.addActionListener(e -> addVenue(add));
        JButton remove = UiKit.ghostButton("Remove selected");
        remove.addActionListener(e -> removeVenue(remove));
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshVenues());

        JLabel help = new JLabel("<html>Venues feed the event form's venue dropdown. "
            + "Removing a venue never rewrites historical events \u2014 they keep their "
            + "venue text.</html>");
        help.setFont(edu.mec.studentportal.client.ui.theme.PortalTheme.XS);
        help.setForeground(edu.mec.studentportal.client.ui.theme.PortalTheme.MUTED);

        JPanel inputRow = actionBar();
        inputRow.add(new JLabel("New venue:"));
        inputRow.add(venueNameField);
        inputRow.add(add);
        inputRow.add(remove);
        inputRow.add(refresh);

        JPanel top = new JPanel();
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        top.setOpaque(false);
        help.setAlignmentX(LEFT_ALIGNMENT);
        inputRow.setAlignmentX(LEFT_ALIGNMENT);
        top.add(help);
        top.add(inputRow);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(venuesTable), BorderLayout.CENTER);
        return panel;
    }

    private static JPanel actionBar() {
        return UiKit.toolbarSurface(new FlowLayout(FlowLayout.LEFT, 8, 0), 10, 12, 10, 12);
    }

    private static JPanel filterControl(String caption, JComponent control, int width) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new javax.swing.BoxLayout(group, javax.swing.BoxLayout.Y_AXIS));
        JLabel label = new JLabel(caption.toUpperCase());
        label.setFont(edu.mec.studentportal.client.ui.theme.PortalTheme.EYEBROW);
        label.setForeground(edu.mec.studentportal.client.ui.theme.PortalTheme.MUTED);
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 2, 5, 0));
        Dimension size = new Dimension(width, 42);
        control.setPreferredSize(size);
        control.setMinimumSize(new Dimension(Math.min(width, 76), 42));
        control.setMaximumSize(new Dimension(width, 42));
        group.add(label);
        group.add(control);
        return group;
    }

    private static void styleFilterCombo(JComboBox<String> combo, int width) {
        combo.setFont(edu.mec.studentportal.client.ui.theme.PortalTheme.BODY);
        combo.setForeground(edu.mec.studentportal.client.ui.theme.PortalTheme.INK);
        combo.setBackground(Color.WHITE);
        combo.setOpaque(true);
        combo.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(
                edu.mec.studentportal.client.ui.theme.PortalTheme.BORDER, 1, true),
            javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 6)));
        combo.setPreferredSize(new Dimension(width, 42));
    }

    private static void styleFilterSpinner(JSpinner spinner) {
        spinner.setFont(edu.mec.studentportal.client.ui.theme.PortalTheme.BODY);
        spinner.setBorder(javax.swing.BorderFactory.createLineBorder(
            edu.mec.studentportal.client.ui.theme.PortalTheme.BORDER, 1, true));
        spinner.setPreferredSize(new Dimension(92, 42));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            defaultEditor.getTextField().setFont(
                edu.mec.studentportal.client.ui.theme.PortalTheme.BODY);
            defaultEditor.getTextField().setHorizontalAlignment(javax.swing.JTextField.LEFT);
            defaultEditor.getTextField().setBorder(
                javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 2));
        }
    }

    private void refreshVenues() {
        status("Loading venues…");
        ApiTask.run(
            () -> api.get("/venues", VENUE_LIST),
            venues -> {
                UiSupport.clear(venuesModel);
                int count = 0;
                if (venues != null) {
                    for (VenueResponse venue : venues) {
                        UiSupport.addRow(venuesModel, venue.id(), venue.name());
                        count++;
                    }
                }
                status("Venues: " + count);
            },
            this::error
        );
    }

    private void addVenue(JButton trigger) {
        String name = venueNameField.getText().trim();
        if (name.isEmpty()) {
            status("Enter a venue name first.");
            return;
        }
        status("Adding venue…");
        ApiTask.run(
            () -> api.post("/admin/venues", new VenueCreateRequest(name),
                VenueResponse.class),
            created -> {
                venueNameField.setText("");
                status("Venue added.");
                refreshVenues();
            },
            this::error,
            trigger
        );
    }

    private void removeVenue(JButton trigger) {
        Long id = UiSupport.selectedId(venuesTable, 0);
        if (id == null) {
            status("Select a venue first.");
            return;
        }
        int row = venuesTable.getSelectedRow();
        String name = String.valueOf(
            venuesModel.getValueAt(venuesTable.convertRowIndexToModel(row), 1));
        if (!UiSupport.confirm(this,
            "Remove venue \"" + name + "\"? Existing events keep their venue text.")) {
            return;
        }
        status("Removing venue…");
        ApiTask.run(
            () -> api.delete("/admin/venues/" + id, VenueResponse.class),
            removed -> {
                status("Venue removed.");
                refreshVenues();
            },
            this::error,
            trigger
        );
    }

    private void resetSelectedPassword(JButton trigger) {
        AdminUserDto user = selectedUser();
        if (user == null) {
            status("Select a user first.");
            return;
        }
        javax.swing.JPasswordField field = new javax.swing.JPasswordField(24);
        edu.mec.studentportal.client.ui.theme.UiKit.enableTextEditing(field);
        JPanel prompt = new JPanel(new BorderLayout(0, 8));
        JLabel caption = new JLabel("New temporary password for " + user.email()
            + " (8\u201372 characters):");
        caption.setFont(edu.mec.studentportal.client.ui.theme.PortalTheme.SM);
        prompt.add(caption, BorderLayout.NORTH);
        prompt.add(field, BorderLayout.CENTER);
        int result = JOptionPane.showConfirmDialog(this, prompt,
            "Reset password #" + user.id(), JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            status("Password reset cancelled.");
            return;
        }
        String password = new String(field.getPassword());
        int bytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) {
            UiSupport.info(this, "Password must be between 8 and 72 UTF-8 bytes.");
            return;
        }
        status("Resetting password…");
        ApiTask.run(
            () -> api.post("/admin/users/" + user.id() + "/reset-password",
                new AdminResetPasswordRequest(password), AdminUserDto.class),
            updated -> {
                status("Password reset — must be changed at next login.");
                JPanel details = new JPanel(new BorderLayout(0, 8));
                JLabel summary = new JLabel("<html>Temporary password stored for <b>"
                    + user.email() + "</b>. The user must change it at next login.<br>"
                    + "Stored BCrypt hash (verification only \u2014 the plaintext is "
                    + "never returned):<br>"
                    + "<span style='font-family:monospace'>"
                    + (updated.passwordHash() == null ? "(none)"
                        : updated.passwordHash())
                    + "</span></html>");
                summary.setFont(edu.mec.studentportal.client.ui.theme.PortalTheme.SM);
                details.add(summary, BorderLayout.CENTER);
                JOptionPane.showMessageDialog(this, details, "Password reset",
                    JOptionPane.INFORMATION_MESSAGE);
                refreshUsers();
            },
            this::error,
            trigger
        );
    }

    private void refreshUsers() {
        StringBuilder path = new StringBuilder("/admin/users?page=")
            .append(userPage.getValue())
            .append("&size=20");
        String role = String.valueOf(userRoleFilter.getSelectedItem());
        if (!"ALL".equals(role)) {
            path.append("&role=").append(role);
        }
        String enabled = String.valueOf(userEnabledFilter.getSelectedItem());
        if ("Enabled".equals(enabled)) {
            path.append("&enabled=true");
        } else if ("Disabled".equals(enabled)) {
            path.append("&enabled=false");
        }
        String query = userSearch.field().getText().trim();
        if (!query.isEmpty()) {
            path.append("&q=").append(java.net.URLEncoder.encode(query,
                java.nio.charset.StandardCharsets.UTF_8));
        }
        String requestPath = path.toString();
        status("Loading users…");
        ApiTask.run(
            () -> api.get(requestPath, AdminUserPageDto.class),
            page -> {
                userPageResult = page;
                UiSupport.clear(usersModel);
                if (page != null) {
                    for (AdminUserDto user : page.items()) {
                        UiSupport.addRow(
                            usersModel,
                            user.id(),
                            user.fullName(),
                            user.email(),
                            user.department(),
                            user.role() == null ? "" : user.role().name(),
                            user.enabled() ? "Yes" : "No",
                            user.passwordHash() == null ? "" : user.passwordHash()
                        );
                    }
                    status("Users: " + page.totalItems() + " total · page "
                        + (page.page() + 1) + "/" + Math.max(page.totalPages(), 1));
                }
            },
            this::error
        );
    }

    private void createUser(JButton trigger) {
        UserFormDialog.Result result = UserFormDialog.show(
            this, true, "Create user", "", "", "", Role.STUDENT, null);
        if (result == null) {
            return;
        }
        status("Creating user…");
        ApiTask.run(
            () -> api.post("/admin/users", new AdminUserCreateRequest(
                result.fullName(), result.email(), result.password(),
                result.department(), result.role(), result.profile()
            ), AdminUserDto.class),
            created -> {
                status("User created.");
                UiSupport.info(this, "Created user #" + created.id() + ".");
                refreshUsers();
            },
            this::error,
            trigger
        );
    }

    private void editUser(JButton trigger) {
        AdminUserDto user = selectedUser();
        if (user == null) {
            status("Select a user first.");
            return;
        }
        UserFormDialog.Result result = UserFormDialog.show(
            this, false, "Edit user #" + user.id(),
            user.fullName(), user.email(), user.department(), user.role(), user.profile());
        if (result == null) {
            return;
        }
        status("Updating user…");
        ApiTask.run(
            () -> api.put("/admin/users/" + user.id(), new AdminUserUpdateRequest(
                result.fullName(), result.email(), result.department(),
                result.role(), result.profile()
            ), AdminUserDto.class),
            updated -> {
                status("User updated.");
                refreshUsers();
            },
            this::error,
            trigger
        );
    }

    private void setUserEnabled(boolean enableState, JButton trigger) {
        AdminUserDto user = selectedUser();
        if (user == null) {
            status("Select a user first.");
            return;
        }
        String verb = enableState ? "Enable" : "Disable";
        if (!UiSupport.confirm(this, verb + " user " + user.email() + "?")) {
            return;
        }
        status(verb + "ing user…");
        ApiTask.run(
            () -> api.post("/admin/users/" + user.id() + "/" + verb.toLowerCase(), null,
                AdminUserDto.class),
            updated -> {
                status("User " + (enableState ? "enabled" : "disabled") + ".");
                refreshUsers();
            },
            this::error,
            trigger
        );
    }

    private void refreshModeration() {
        String filter = String.valueOf(moderationFilter.getSelectedItem());
        String path = "ALL".equals(filter) ? "/events" : "/events?status=" + filter;
        status("Loading events…");
        ApiTask.run(
            () -> api.get(path, EVENT_LIST),
            loaded -> {
                moderationRows = loaded == null ? List.of() : loaded;
                UiSupport.clear(moderationModel);
                for (EventDto event : moderationRows) {
                    UiSupport.addRow(
                        moderationModel,
                        event.id(),
                        event.title(),
                        event.venue(),
                        UiSupport.dateTime(event.startTime()),
                        UiSupport.dateTime(event.endTime()),
                        event.capacity(),
                        event.status() == null ? "" : event.status().name(),
                        event.reviewReason() == null ? "" : event.reviewReason()
                    );
                }
                status("Events: " + moderationRows.size());
            },
            this::error
        );
    }

    private void moderate(JButton trigger, boolean approve) {
        EventDto event = selectedModerationEvent();
        if (event == null) {
            status("Select an event first.");
            return;
        }
        if (approve) {
            if (!UiSupport.confirm(this, "Approve event #" + event.id() + "?")) {
                return;
            }
            status("Approving…");
            ApiTask.run(
                () -> api.post("/events/" + event.id() + "/approve", null, EventDto.class),
                ignored -> {
                    status("Approved.");
                    refreshModeration();
                },
                this::error,
                trigger
            );
            return;
        }
        String reason = JOptionPane.showInputDialog(
            this, "Rejection reason (required):", "Reject event #" + event.id(),
            JOptionPane.PLAIN_MESSAGE);
        if (reason == null || reason.isBlank()) {
            status("Rejection cancelled — a reason is required.");
            return;
        }
        status("Rejecting…");
        ApiTask.run(
            () -> api.post("/events/" + event.id() + "/reject",
                new RejectRequest(reason.trim()), EventDto.class),
            ignored -> {
                status("Rejected.");
                refreshModeration();
            },
            this::error,
            trigger
        );
    }

    private void cancelModerationEvent(JButton trigger) {
        EventDto event = selectedModerationEvent();
        if (event == null) {
            status("Select an event first.");
            return;
        }
        if (!UiSupport.confirm(this,
            "Cancel event #" + event.id() + "? Confirmed seats will be released.")) {
            return;
        }
        status("Cancelling event…");
        ApiTask.run(
            () -> api.post("/events/" + event.id() + "/cancel", null, EventDto.class),
            ignored -> {
                status("Event cancelled.");
                refreshModeration();
            },
            this::error,
            trigger
        );
    }

    private void moderationDetails() {
        EventDto event = selectedModerationEvent();
        if (event == null) {
            status("Select an event first.");
            return;
        }
        Map<String, String> rows = UiSupport.rows();
        rows.put("Title", event.title());
        rows.put("Description", event.description());
        rows.put("Venue", event.venue());
        rows.put("Starts", UiSupport.dateTime(event.startTime()));
        rows.put("Ends", UiSupport.dateTime(event.endTime()));
        rows.put("Status", String.valueOf(event.status()));
        rows.put("Organizer id", String.valueOf(event.createdBy()));
        rows.put("Registered", String.valueOf(event.registeredCount()));
        if (event.reviewReason() != null && !event.reviewReason().isBlank()) {
            rows.put("Review reason", event.reviewReason());
        }
        UiSupport.details(this, "Event #" + event.id(), rows);
    }

    private void refreshClubs() {
        status("Loading clubs…");
        ApiTask.run(
            () -> api.get("/clubs", CLUB_LIST),
            loaded -> {
                clubRows = loaded == null ? List.of() : loaded;
                UiSupport.clear(clubsModel);
                for (ClubDto club : clubRows) {
                    UiSupport.addRow(
                        clubsModel,
                        club.id(),
                        club.name(),
                        club.category(),
                        club.active() ? "Yes" : "No",
                        club.activeMemberCount()
                    );
                }
                status("Clubs: " + clubRows.size());
            },
            this::error
        );
    }

    private void clubForm(JButton trigger, ClubDto existing) {
        String title = existing == null ? "Create club" : "Edit club #" + existing.id();
        String[] values = UiSupport.form(
            this, title,
            new String[] { "Name", "Description", "Category" },
            existing == null
                ? new String[] { "", "", "" }
                : new String[] { existing.name(), existing.description(), existing.category() });
        if (values == null) {
            return;
        }
        if (values[0].isEmpty() || values[1].isEmpty() || values[2].isEmpty()) {
            UiSupport.info(this, "Name, description and category are required.");
            return;
        }
        ClubRequest request = new ClubRequest(values[0], values[1], values[2]);
        status(existing == null ? "Creating club…" : "Updating club…");
        ApiTask.run(
            existing == null
                ? () -> api.post("/clubs", request, ClubDto.class)
                : () -> api.put("/clubs/" + existing.id(), request, ClubDto.class),
            saved -> {
                status("Club saved.");
                refreshClubs();
            },
            this::error,
            trigger
        );
    }

    private void deactivateClub(JButton trigger) {
        ClubDto club = selectedClub();
        if (club == null) {
            status("Select a club first.");
            return;
        }
        if (!UiSupport.confirm(this, "Deactivate club " + club.name()
            + "? Memberships and history are kept.")) {
            return;
        }
        status("Deactivating club…");
        ApiTask.run(
            () -> api.post("/clubs/" + club.id() + "/deactivate", null, ClubDto.class),
            deactivated -> {
                status("Club deactivated.");
                refreshClubs();
            },
            this::error,
            trigger
        );
    }

    private void showMembers(JButton trigger) {
        ClubDto club = selectedClub();
        if (club == null) {
            status("Select a club first.");
            return;
        }
        status("Loading members…");
        ApiTask.run(
            () -> api.get("/clubs/" + club.id() + "/members", MEMBER_LIST),
            members -> {
                status(" ");
                DefaultTableModel model = UiSupport.tableModel(
                    "Membership ID", "Student ID", "Name", "Roll", "Status", "Joined");
                if (members != null) {
                    for (ClubMemberDto member : members) {
                        UiSupport.addRow(
                            model,
                            member.membershipId(),
                            member.studentId(),
                            member.fullName(),
                            member.rollNumber(),
                            member.status() == null ? "" : member.status().name(),
                            UiSupport.dateTime(member.joinedAt())
                        );
                    }
                }
                JTable table = new JTable(model);
                JOptionPane.showMessageDialog(
                    this, new JScrollPane(table),
                    "Members of " + club.name(), JOptionPane.INFORMATION_MESSAGE);
            },
            this::error,
            trigger
        );
    }

    private void refreshAnnouncements() {
        status("Loading announcements…");
        ApiTask.run(
            () -> api.get("/announcements", ANNOUNCEMENT_LIST),
            loaded -> {
                announcementRows = loaded == null ? List.of() : loaded;
                UiSupport.clear(announcementsModel);
                for (AnnouncementDto announcement : announcementRows) {
                    UiSupport.addRow(
                        announcementsModel,
                        announcement.id(),
                        announcement.title(),
                        announcement.audienceRole() == null
                            ? "Everyone" : announcement.audienceRole(),
                        announcement.status() == null ? "" : announcement.status().name(),
                        UiSupport.dateTime(announcement.createdAt())
                    );
                }
                status("Announcements: " + announcementRows.size());
            },
            this::error
        );
    }

    private void postAnnouncement(JButton trigger) {
        JPanel main = new JPanel(new java.awt.BorderLayout(8, 8));
        JPanel form = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(4, 4, 4, 4);
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Title"), gbc);
        gbc.gridx = 1;
        javax.swing.JTextField titleField = new javax.swing.JTextField(28);
        edu.mec.studentportal.client.ui.theme.UiKit.enableTextEditing(titleField);
        form.add(titleField, gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Audience"), gbc);
        gbc.gridx = 1;
        JComboBox<String> audienceBox = new JComboBox<>(new String[] {"ALL", "STUDENT", "FACULTY", "ORGANIZER", "ADMIN"});
        form.add(audienceBox, gbc);
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = java.awt.GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Content"), gbc);
        gbc.gridx = 1; gbc.fill = java.awt.GridBagConstraints.BOTH;
        javax.swing.JTextArea contentArea = new javax.swing.JTextArea(8, 28);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0xCCCCCC)));
        edu.mec.studentportal.client.ui.theme.UiKit.enableTextEditing(contentArea);
        javax.swing.JScrollPane contentScroll = new javax.swing.JScrollPane(contentArea);
        contentScroll.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0xCCCCCC)));
        contentScroll.setPreferredSize(new java.awt.Dimension(340, 160));
        form.add(contentScroll, gbc);
        main.add(form, java.awt.BorderLayout.CENTER);
        int result = JOptionPane.showConfirmDialog(this, main, "Post announcement", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        String title = titleField.getText().trim();
        String content = contentArea.getText().trim();
        if (title.isEmpty() || content.isEmpty()) {
            UiSupport.info(this, "Title and content are required.");
            return;
        }
        Object sel = audienceBox.getSelectedItem();
        String audience = sel == null || "ALL".equals(sel) ? null : sel.toString();
        status("Publishing announcement…");
        ApiTask.run(
            () -> api.post("/announcements",
                new AnnouncementRequest(title, content, audience),
                AnnouncementDto.class),
            created -> {
                status("Announcement published.");
                refreshAnnouncements();
            },
            this::error,
            trigger
        );
    }


    private void showAnnouncementDetails() {
        int row = announcementsTable.getSelectedRow();
        if (row < 0 || announcementRows == null) {
            return;
        }
        int modelRow = announcementsTable.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= announcementRows.size()) {
            return;
        }
        AnnouncementDto ann = announcementRows.get(modelRow);
        JPanel panel = new JPanel(new java.awt.BorderLayout(6, 6));
        JLabel title = new JLabel(ann.title() == null ? "" : ann.title());
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        panel.add(title, java.awt.BorderLayout.NORTH);
        JTextArea content = new JTextArea(ann.content() == null ? "" : ann.content());
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setBackground(panel.getBackground());
        JScrollPane sp = new JScrollPane(content);
        sp.setPreferredSize(new java.awt.Dimension(400, 200));
        panel.add(sp, java.awt.BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, panel, "Announcement", JOptionPane.INFORMATION_MESSAGE);
    }

    private void archiveAnnouncement(JButton trigger) {
        int row = announcementsTable.getSelectedRow();
        if (row < 0) {
            status("Select an announcement first.");
            return;
        }
        long id = ((Number) announcementsModel.getValueAt(
            announcementsTable.convertRowIndexToModel(row), 0)).longValue();
        if (!UiSupport.confirm(this, "Archive announcement #" + id + "?")) {
            return;
        }
        status("Archiving…");
        ApiTask.run(
            () -> api.post("/announcements/" + id + "/archive", null, AnnouncementDto.class),
            archived -> {
                status("Announcement archived.");
                refreshAnnouncements();
            },
            this::error,
            trigger
        );
    }

    private AdminUserDto selectedUser() {
        int row = usersTable.getSelectedRow();
        if (row < 0 || userPageResult == null) {
            return null;
        }
        long id = ((Number) usersModel.getValueAt(usersTable.convertRowIndexToModel(row), 0))
            .longValue();
        for (AdminUserDto user : userPageResult.items()) {
            if (user.id() == id) {
                return user;
            }
        }
        return null;
    }

    private EventDto selectedModerationEvent() {
        int row = moderationTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        long id = ((Number) moderationModel.getValueAt(
            moderationTable.convertRowIndexToModel(row), 0)).longValue();
        for (EventDto event : moderationRows) {
            if (event.id() == id) {
                return event;
            }
        }
        return null;
    }

    private ClubDto selectedClub() {
        int row = clubsTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        long id = ((Number) clubsModel.getValueAt(clubsTable.convertRowIndexToModel(row), 0))
            .longValue();
        for (ClubDto club : clubRows) {
            if (club.id() == id) {
                return club;
            }
        }
        return null;
    }

    /** Small text field bundled with its action component access for filters. */
    private static final class JTextFieldWithAction {
        private final UiKit.HintTextField field = UiKit.hintField("Name or email");

        private JTextFieldWithAction() {
            field.setPreferredSize(new Dimension(210, 42));
            field.setMinimumSize(new Dimension(180, 42));
        }

        javax.swing.JTextField field() {
            return field;
        }
    }

}
