package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.ui.theme.UiKit;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.AnnouncementDto;
import edu.mec.studentportal.client.dto.ClubDto;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.FeedbackDto;
import edu.mec.studentportal.client.dto.FeedbackRequest;
import edu.mec.studentportal.client.dto.FeedbackSummaryDto;
import edu.mec.studentportal.client.dto.MembershipDto;
import edu.mec.studentportal.client.dto.NotificationDto;
import edu.mec.studentportal.client.dto.RegistrationDto;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

/**
 * Student role dashboard: browse approved events and register/cancel, manage club
 * memberships, read announcements and notifications, and submit feedback after events end.
 */
public final class StudentDashboard extends DashboardPanel {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final TypeReference<List<EventDto>> EVENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<RegistrationDto>> REGISTRATION_LIST =
        new TypeReference<>() {};
    private static final TypeReference<List<ClubDto>> CLUB_LIST = new TypeReference<>() {};
    private static final TypeReference<List<AnnouncementDto>> ANNOUNCEMENT_LIST =
        new TypeReference<>() {};
    private static final TypeReference<List<NotificationDto>> NOTIFICATION_LIST =
        new TypeReference<>() {};

    private final DefaultTableModelHolder events = new DefaultTableModelHolder(
        "ID", "Title", "Venue", "Starts", "Ends", "Capacity", "Registered", "Available", "Status"
    );
    private final DefaultTableModelHolder registrations = new DefaultTableModelHolder(
        "Registration ID", "Event ID", "Status", "Ticket", "Registered at"
    );
    private final DefaultTableModelHolder clubs = new DefaultTableModelHolder(
        "ID", "Name", "Category", "Active", "Members"
    );
    private final DefaultTableModelHolder announcements = new DefaultTableModelHolder(
        "ID", "Title", "Audience", "Status", "Created"
    );
    private final DefaultTableModelHolder notifications = new DefaultTableModelHolder(
        "ID", "Type", "Title", "Message", "Created", "Read"
    );

    private List<EventDto> eventRows = List.of();
    private List<RegistrationDto> registrationRows = List.of();
    private List<ClubDto> clubRows = List.of();
    private List<AnnouncementDto> announcementRows = List.of();

    private final JComboBox<EventChoice> feedbackEvents = new JComboBox<>();
    private final JSpinner feedbackRating = new JSpinner(new SpinnerNumberModel(5, 1, 5, 1));
    private final JTextArea feedbackComments = new JTextArea(3, 32);
    private final JButton feedbackSubmit = UiKit.ghostButton("Submit feedback");
    private final JButton feedbackSummary = UiKit.ghostButton("Show summary");

    public StudentDashboard(ApiClient api, SessionContext session) {
        super("Student Dashboard", api, session);
        tabs.addTab("Events", buildEventsTab());
        tabs.addTab("My registrations", buildRegistrationsTab());
        tabs.addTab("Clubs", buildClubsTab());
        tabs.addTab("Announcements", buildAnnouncementsTab());
        tabs.addTab("Notifications", buildNotificationsTab());
        tabs.addTab("Feedback", buildFeedbackTab());
    }

    @Override
    public void refreshAll() {
        refreshEvents();
        refreshRegistrations();
        refreshClubs();
        refreshAnnouncements();
        refreshNotifications();
        refreshFeedbackChoices();
    }

    private JPanel buildEventsTab() {
        JTable table = events.table();
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshEvents());
        JButton details = UiKit.ghostButton("Details");
        details.addActionListener(e -> showEventDetails(table));
        JButton register = UiKit.ghostButton("Register");
        register.addActionListener(e -> registerSelected(table, register));
        JButton cancelSeat = UiKit.ghostButton("Cancel my seat");
        cancelSeat.addActionListener(e -> cancelSelectedSeat(table, cancelSeat));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(refresh);
        buttons.add(details);
        buttons.add(register);
        buttons.add(cancelSeat);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRegistrationsTab() {
        JTable table = registrations.table();
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshRegistrations());
        JButton cancel = UiKit.ghostButton("Cancel selected");
        cancel.addActionListener(e -> cancelFromHistory(table, cancel));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(refresh);
        buttons.add(cancel);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildClubsTab() {
        JTable table = clubs.table();
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshClubs());
        JButton details = UiKit.ghostButton("Details");
        details.addActionListener(e -> showClubDetails(table));
        JButton join = UiKit.ghostButton("Join");
        join.addActionListener(e -> changeMembership(table, join, true));
        JButton leave = UiKit.ghostButton("Leave");
        leave.addActionListener(e -> changeMembership(table, leave, false));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(refresh);
        buttons.add(details);
        buttons.add(join);
        buttons.add(leave);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAnnouncementsTab() {
        JTable table = announcements.table();
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshAnnouncements());
        JButton details = UiKit.ghostButton("Details");
        details.addActionListener(e -> showAnnouncementDetails(table));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(refresh);
        buttons.add(details);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        panel.add(scroll, BorderLayout.CENTER);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showAnnouncementDetails(table);
                }
            }
        });
        return panel;
    }

    private JPanel buildNotificationsTab() {
        JTable table = notifications.table();
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshNotifications());
        JButton markRead = UiKit.ghostButton("Mark read");
        markRead.addActionListener(e -> markNotificationRead(table));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(refresh);
        buttons.add(markRead);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFeedbackTab() {
        feedbackComments.setLineWrap(true);
        feedbackComments.setWrapStyleWord(true);
        JPanel form = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new BorderLayout(6, 6));
        JPanel ratingRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ratingRow.add(new javax.swing.JLabel("Finished event:"));
        ratingRow.add(feedbackEvents);
        ratingRow.add(new javax.swing.JLabel("Rating:"));
        ratingRow.add(feedbackRating);
        top.add(ratingRow, BorderLayout.NORTH);
        top.add(new JScrollPane(feedbackComments), BorderLayout.CENTER);

        feedbackSubmit.addActionListener(e -> submitFeedback());
        feedbackSummary.addActionListener(e -> showFeedbackSummary());
        JButton refresh = UiKit.ghostButton("Refresh events");
        refresh.addActionListener(e -> refreshFeedbackChoices());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(refresh);
        buttons.add(feedbackSubmit);
        buttons.add(feedbackSummary);

        form.add(buttons, BorderLayout.NORTH);
        form.add(top, BorderLayout.CENTER);
        return form;
    }

    private void refreshEvents() {
        status("Loading events…");
        ApiTask.run(
            () -> api.get("/events", EVENT_LIST),
            rows -> {
                eventRows = rows == null ? List.of() : rows;
                UiSupport.clear(events.model());
                for (EventDto event : eventRows) {
                    UiSupport.addRow(
                        events.model(),
                        event.id(),
                        event.title(),
                        event.venue(),
                        UiSupport.dateTime(event.startTime()),
                        UiSupport.dateTime(event.endTime()),
                        event.capacity(),
                        event.registeredCount(),
                        event.availableSlots(),
                        event.status() == null ? "" : event.status().name()
                    );
                }
                status("Events: " + eventRows.size());
            },
            this::error
        );
    }

    private void refreshRegistrations() {
        status("Loading registrations…");
        ApiTask.run(
            () -> api.get("/registrations/me", REGISTRATION_LIST),
            rows -> {
                registrationRows = rows == null ? List.of() : rows;
                UiSupport.clear(registrations.model());
                for (RegistrationDto registration : registrationRows) {
                    UiSupport.addRow(
                        registrations.model(),
                        registration.registrationId(),
                        registration.eventId(),
                        registration.status() == null ? "" : registration.status().name(),
                        registration.ticketNo(),
                        UiSupport.dateTime(registration.registeredAt())
                    );
                }
                status("My registrations: " + registrationRows.size());
            },
            this::error
        );
    }

    private void refreshClubs() {
        status("Loading clubs…");
        ApiTask.run(
            () -> api.get("/clubs", CLUB_LIST),
            rows -> {
                clubRows = rows == null ? List.of() : rows;
                UiSupport.clear(clubs.model());
                for (ClubDto club : clubRows) {
                    UiSupport.addRow(
                        clubs.model(),
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

    private void refreshAnnouncements() {
        status("Loading announcements…");
        ApiTask.run(
            () -> api.get("/announcements", ANNOUNCEMENT_LIST),
            rows -> {
                announcementRows = rows == null ? List.of() : rows;
                UiSupport.clear(announcements.model());
                for (AnnouncementDto announcement : announcementRows) {
                    UiSupport.addRow(
                        announcements.model(),
                        announcement.id(),
                        announcement.title(),
                        announcement.audienceRole() == null ? "Everyone" : announcement.audienceRole(),
                        announcement.status() == null ? "" : announcement.status().name(),
                        UiSupport.dateTime(announcement.createdAt())
                    );
                }
                status("Announcements: " + announcementRows.size());
            },
            this::error
        );
    }

    private void refreshNotifications() {
        status("Loading notifications…");
        ApiTask.run(
            () -> api.get("/notifications/me", NOTIFICATION_LIST),
            rows -> {
                List<NotificationDto> list = rows == null ? List.of() : rows;
                UiSupport.clear(notifications.model());
                for (NotificationDto notification : list) {
                    UiSupport.addRow(
                        notifications.model(),
                        notification.id(),
                        notification.notificationType() == null
                            ? "" : notification.notificationType().name(),
                        notification.title(),
                        notification.message(),
                        UiSupport.dateTime(notification.createdAt()),
                        notification.readAt() == null ? "Unread" : "Read"
                    );
                }
                status("Notifications: " + list.size());
            },
            this::error
        );
    }

    private void registerSelected(JTable table, JButton trigger) {
        Long eventId = UiSupport.selectedId(table, 0);
        if (eventId == null) {
            status("Select an event first.");
            return;
        }
        if (!UiSupport.confirm(this, "Register for event #" + eventId + "?")) {
            return;
        }
        status("Registering…");
        ApiTask.run(
            () -> api.post("/events/" + eventId + "/registrations", null,
                edu.mec.studentportal.client.dto.RegistrationDto.class),
            registration -> {
                status("Registered.");
                UiSupport.info(this, "Registration confirmed.\nTicket: "
                    + (registration == null ? "-" : registration.ticketNo()));
                refreshEvents();
                refreshRegistrations();
            },
            this::error,
            trigger
        );
    }

    private void cancelSelectedSeat(JTable table, JButton trigger) {
        cancelSeatForSelectedEvent(table, trigger, "Cancel your seat for this event?");
    }

    private void cancelFromHistory(JTable table, JButton trigger) {
        cancelSeatForSelectedEvent(table, trigger, "Cancel your registration for this event?");
    }

    private void cancelSeatForSelectedEvent(JTable table, JButton trigger, String prompt) {
        Long eventId = UiSupport.selectedId(table, table == events.table() ? 0 : 1);
        if (eventId == null) {
            status("Select a registration first.");
            return;
        }
        if (!UiSupport.confirm(this, prompt + "\n(Event #" + eventId + ")")) {
            return;
        }
        status("Cancelling registration…");
        ApiTask.run(
            () -> api.delete("/events/" + eventId + "/registrations/me", RegistrationDto.class),
            ignored -> {
                status("Registration cancelled.");
                refreshEvents();
                refreshRegistrations();
            },
            this::error,
            trigger
        );
    }

    private void changeMembership(JTable table, JButton trigger, boolean join) {
        Long clubId = UiSupport.selectedId(table, 0);
        if (clubId == null) {
            status("Select a club first.");
            return;
        }
        String prompt = join
            ? "Join this club?"
            : "Leave this club? Your membership history is kept.";
        if (!UiSupport.confirm(this, prompt)) {
            return;
        }
        status(join ? "Joining club…" : "Leaving club…");
        ApiTask.run(
            join
                ? () -> api.post("/clubs/" + clubId + "/membership", null, MembershipDto.class)
                : () -> api.delete("/clubs/" + clubId + "/membership", MembershipDto.class),
            membership -> {
                status(join ? "Joined club." : "Left club.");
                if (membership != null && membership.status() != null) {
                    UiSupport.info(this, "Membership status: " + membership.status());
                }
                refreshClubs();
            },
            this::error,
            trigger
        );
    }

    private void showEventDetails(JTable table) {
        Long id = UiSupport.selectedId(table, 0);
        EventDto event = findById(eventRows, id);
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
        rows.put("Capacity", String.valueOf(event.capacity()));
        rows.put("Registered", String.valueOf(event.registeredCount()));
        rows.put("Available", String.valueOf(event.availableSlots()));
        rows.put("Status", UiSupport.text(event.status()));
        if (event.reviewReason() != null && !event.reviewReason().isBlank()) {
            rows.put("Review reason", event.reviewReason());
        }
        UiSupport.details(this, "Event #" + event.id(), rows);
    }

    private void showClubDetails(JTable table) {
        Long id = UiSupport.selectedId(table, 0);
        ClubDto club = findById(clubRows, id);
        if (club == null) {
            status("Select a club first.");
            return;
        }
        Map<String, String> rows = UiSupport.rows();
        rows.put("Name", club.name());
        rows.put("Description", club.description());
        rows.put("Category", club.category());
        rows.put("Active", club.active() ? "Yes" : "No");
        rows.put("Active members", String.valueOf(club.activeMemberCount()));
        UiSupport.details(this, "Club " + club.name(), rows);
    }

    private void showAnnouncementDetails(JTable table) {
        Long id = UiSupport.selectedId(table, 0);
        AnnouncementDto announcement = findById(announcementRows, id);
        if (announcement == null) {
            status("Select an announcement first.");
            return;
        }
        JPanel panel = new JPanel(new java.awt.BorderLayout(6, 6));
        JLabel title = new JLabel(announcement.title() == null ? "" : announcement.title());
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        panel.add(title, java.awt.BorderLayout.NORTH);
        JTextArea content = new JTextArea(announcement.content() == null ? "" : announcement.content());
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setBackground(panel.getBackground());
        JScrollPane sp = new JScrollPane(content);
        sp.setPreferredSize(new java.awt.Dimension(420, 220));
        panel.add(sp, java.awt.BorderLayout.CENTER);
        JPanel meta = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        meta.add(new JLabel("Audience: " + (announcement.audienceRole() == null ? "Everyone" : announcement.audienceRole())));
        meta.add(new JLabel(" | Status: " + UiSupport.text(announcement.status())));
        meta.add(new JLabel(" | Created: " + UiSupport.dateTime(announcement.createdAt())));
        panel.add(meta, java.awt.BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, panel, "Announcement", JOptionPane.INFORMATION_MESSAGE);
    }

    private void markNotificationRead(JTable table) {
        Long id = UiSupport.selectedId(table, 0);
        if (id == null) {
            status("Select a notification first.");
            return;
        }
        status("Marking read…");
        ApiTask.run(
            () -> api.patch("/notifications/" + id + "/read", null, NotificationDto.class),
            ignored -> {
                status("Marked as read.");
                refreshNotifications();
            },
            this::error
        );
    }

    private void refreshFeedbackChoices() {
        status("Loading feedback options…");
        ApiTask.run(
            () -> {
                List<RegistrationDto> own = api.get("/registrations/me", REGISTRATION_LIST);
                List<EventDto> visible = api.get("/events", EVENT_LIST);
                return new Object[] { own, visible };
            },
            payload -> {
                @SuppressWarnings("unchecked")
                List<RegistrationDto> own = (List<RegistrationDto>) payload[0];
                @SuppressWarnings("unchecked")
                List<EventDto> visible = (List<EventDto>) payload[1];
                LocalDateTime now = LocalDateTime.now(CAMPUS_ZONE);
                Map<Long, EventDto> byId = new LinkedHashMap<>();
                for (EventDto event : visible) {
                    byId.put(event.id(), event);
                }
                feedbackEvents.removeAllItems();
                List<RegistrationDto> effectiveRegistrations = own == null ? List.of() : own;
                for (RegistrationDto registration : effectiveRegistrations) {
                    EventDto event = byId.get(registration.eventId());
                    if (event != null
                        && event.endTime() != null
                        && event.endTime().isBefore(now)
                        && registration.status() != null
                        && registration.status().name().equals("CONFIRMED")) {
                        feedbackEvents.addItem(new EventChoice(
                            event.id(), "#" + event.id() + " — " + event.title()
                        ));
                    }
                }
                int count = feedbackEvents.getItemCount();
                status(count == 0
                    ? "No finished events with a confirmed seat yet."
                    : "Finished events ready for feedback: " + count);
            },
            this::error
        );
    }

    private void submitFeedback() {
        EventChoice choice = (EventChoice) feedbackEvents.getSelectedItem();
        if (choice == null) {
            status("No eligible event selected.");
            return;
        }
        int rating = (Integer) feedbackRating.getValue();
        String comments = feedbackComments.getText().trim();
        status("Submitting feedback…");
        ApiTask.run(
            () -> api.post("/events/" + choice.id() + "/feedback",
                new FeedbackRequest(rating, comments.isEmpty() ? null : comments), FeedbackDto.class),
            ignored -> {
                status("Feedback submitted. Thank you!");
                UiSupport.info(this, "Feedback submitted for event #" + choice.id() + ".");
                feedbackComments.setText("");
            },
            this::error,
            feedbackSubmit
        );
    }

    private void showFeedbackSummary() {
        EventChoice choice = (EventChoice) feedbackEvents.getSelectedItem();
        if (choice == null) {
            status("No event selected.");
            return;
        }
        status("Loading summary…");
        ApiTask.run(
            () -> api.get("/events/" + choice.id() + "/feedback/summary", FeedbackSummaryDto.class),
            summary -> {
                status(" ");
                if (summary == null) {
                    UiSupport.info(this, "No feedback yet for this event.");
                    return;
                }
                UiSupport.info(this, "Average rating: "
                    + String.format(java.util.Locale.ROOT, "%.2f", summary.averageRating())
                    + " from " + summary.ratingCount() + " rating(s).");
            },
            this::error
        );
    }

    private static <T> T findById(List<T> rows, Long id) {
        if (id == null || rows == null) {
            return null;
        }
        for (T row : rows) {
            long rowId = switch (row) {
                case EventDto event -> event.id();
                case ClubDto club -> club.id();
                case AnnouncementDto announcement -> announcement.id();
                case RegistrationDto registration -> registration.registrationId();
                default -> -1;
            };
            if (rowId == id) {
                return row;
            }
        }
        return null;
    }

    private record EventChoice(long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Model + non-editable table pair kept in sync for a single tab. */
    private static final class DefaultTableModelHolder {
        private final javax.swing.table.DefaultTableModel model;
        private final JTable table;

        private DefaultTableModelHolder(String... columns) {
            this.model = UiSupport.tableModel(columns);
            this.table = new JTable(model);
            this.table.setAutoCreateRowSorter(true);
        }

        javax.swing.table.DefaultTableModel model() {
            return model;
        }

        JTable table() {
            return table;
        }
    }
}
