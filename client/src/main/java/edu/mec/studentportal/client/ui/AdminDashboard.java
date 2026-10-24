package edu.mec.studentportal.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.AdminUserCreateRequest;
import edu.mec.studentportal.client.dto.AdminUserDto;
import edu.mec.studentportal.client.dto.AdminUserPageDto;
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
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
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

    private final DefaultTableModel usersModel = UiSupport.tableModel(
        "ID", "Name", "Email", "Department", "Role", "Enabled"
    );
    private final JTable usersTable = UiSupport.configureTable(new JTable(usersModel));
    private final JComboBox<String> userRoleFilter = new JComboBox<>(
        new String[] { "ALL", "STUDENT", "ORGANIZER", "FACULTY", "ADMIN" });
    private final JComboBox<String> userEnabledFilter = new JComboBox<>(
        new String[] { "ALL", "Enabled", "Disabled" });
    private final JTextFieldWithAction userSearch = new JTextFieldWithAction();
    private final JSpinner userPage = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
    private AdminUserPageDto userPageResult;

    private final DefaultTableModel moderationModel = UiSupport.tableModel(
        "ID", "Title", "Venue", "Starts", "Ends", "Cap", "Status", "Reason");
    private final JTable moderationTable = UiSupport.configureTable(new JTable(moderationModel));
    private final JComboBox<String> moderationFilter = new JComboBox<>(
        new String[] { "PENDING_APPROVAL", "ALL", "APPROVED", "REJECTED", "CANCELLED" });
    private List<EventDto> moderationRows = List.of();

    private final DefaultTableModel clubsModel = UiSupport.tableModel(
        "ID", "Name", "Category", "Active", "Members");
    private final JTable clubsTable = UiSupport.configureTable(new JTable(clubsModel));
    private List<ClubDto> clubRows = List.of();

    private final DefaultTableModel announcementsModel = UiSupport.tableModel(
        "ID", "Title", "Audience", "Status", "Created");
    private final JTable announcementsTable = UiSupport.configureTable(
        new JTable(announcementsModel)
    );
    private List<AnnouncementDto> announcementRows = List.of();

    private final JLabel reportSummary = new JLabel(" ");

    public AdminDashboard(ApiClient api, SessionContext session) {
        super("Admin Dashboard", api, session);
        tabs.addTab("Users", buildUsersTab());
        tabs.addTab("Event moderation", buildModerationTab());
        tabs.addTab("Clubs", buildClubsTab());
        tabs.addTab("Announcements", buildAnnouncementsTab());
        tabs.addTab("Reports", buildReportsTab());
    }

    @Override
    public void refreshAll() {
        refreshUsers();
        refreshModeration();
        refreshClubs();
        refreshAnnouncements();
        refreshReport();
    }

    private JPanel buildUsersTab() {
        JButton apply = new JButton("Apply filters");
        apply.addActionListener(e -> refreshUsers());
        userSearch.field().addActionListener(e -> refreshUsers());
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Role:"));
        filters.add(userRoleFilter);
        filters.add(new JLabel("Status:"));
        filters.add(userEnabledFilter);
        filters.add(new JLabel("Search:"));
        filters.add(userSearch.field());
        filters.add(new JLabel("Page:"));
        filters.add(userPage);
        filters.add(apply);
        userRoleFilter.addActionListener(e -> refreshUsers());
        userEnabledFilter.addActionListener(e -> refreshUsers());

        JButton create = new JButton("Create user");
        create.addActionListener(e -> createUser(create));
        JButton edit = new JButton("Edit user");
        edit.addActionListener(e -> editUser(edit));
        JButton enable = new JButton("Enable");
        enable.addActionListener(e -> setUserEnabled(true, enable));
        JButton disable = new JButton("Disable");
        disable.addActionListener(e -> setUserEnabled(false, disable));
        JButton next = new JButton("Next page");
        next.addActionListener(e -> {
            if (userPageResult != null && userPageResult.page() + 1 < userPageResult.totalPages()) {
                userPage.setValue(userPageResult.page() + 1);
            }
            refreshUsers();
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(create);
        actions.add(edit);
        actions.add(enable);
        actions.add(disable);
        actions.add(next);

        JPanel panel = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new BorderLayout());
        top.add(filters, BorderLayout.NORTH);
        top.add(actions, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildModerationTab() {
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshModeration());
        moderationFilter.addActionListener(e -> refreshModeration());
        JButton approve = new JButton("Approve");
        approve.addActionListener(e -> moderate(approve, true));
        JButton reject = new JButton("Reject…");
        reject.addActionListener(e -> moderate(reject, false));
        JButton cancel = new JButton("Cancel event");
        cancel.addActionListener(e -> cancelModerationEvent(cancel));
        JButton details = new JButton("Details");
        details.addActionListener(e -> moderationDetails());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
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
        JButton create = new JButton("Create club");
        create.addActionListener(e -> clubForm(create, null));
        JButton edit = new JButton("Edit club");
        edit.addActionListener(e -> clubForm(edit, selectedClub()));
        JButton deactivate = new JButton("Deactivate");
        deactivate.addActionListener(e -> deactivateClub(deactivate));
        JButton members = new JButton("Members");
        members.addActionListener(e -> showMembers(members));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshClubs());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
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
        JButton post = new JButton("Post announcement");
        post.addActionListener(e -> postAnnouncement(post));
        JButton archive = new JButton("Archive");
        archive.addActionListener(e -> archiveAnnouncement(archive));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshAnnouncements());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(post);
        buttons.add(archive);
        buttons.add(refresh);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(announcementsTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildReportsTab() {
        JButton refresh = new JButton("Refresh report");
        refresh.addActionListener(e -> refreshReport());
        JPanel top = new JPanel(new BorderLayout());
        top.add(refresh, BorderLayout.NORTH);
        top.add(reportSummary, BorderLayout.CENTER);

        JTable table = UiSupport.configureTable(new JTable(UiSupport.tableModel(
            "Event ID", "Title", "Status", "Capacity", "Confirmed", "Cancelled",
            "Feedback", "Avg rating")));
        table.putClientProperty("terminateEditOnKeyStroke", Boolean.TRUE);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(top, BorderLayout.NORTH);
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
                            user.enabled() ? "Yes" : "No"
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
                JTable table = UiSupport.configureTable(new JTable(model));
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
        String[] values = UiSupport.form(this, "Post announcement",
            new String[] { "Title", "Content", "Audience (blank=Everyone)" },
            new String[] { "", "", "" });
        if (values == null) {
            return;
        }
        if (values[0].isEmpty() || values[1].isEmpty()) {
            UiSupport.info(this, "Title and content are required.");
            return;
        }
        status("Publishing announcement…");
        ApiTask.run(
            () -> api.post("/announcements",
                new AnnouncementRequest(values[0], values[1],
                    values[2].isEmpty() ? null : values[2]),
                AnnouncementDto.class),
            created -> {
                status("Announcement published.");
                refreshAnnouncements();
            },
            this::error,
            trigger
        );
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
        private final javax.swing.JTextField field = new javax.swing.JTextField(14);

        javax.swing.JTextField field() {
            return field;
        }
    }

}
