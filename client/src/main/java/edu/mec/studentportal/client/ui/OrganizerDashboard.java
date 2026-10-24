package edu.mec.studentportal.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.AnnouncementDto;
import edu.mec.studentportal.client.dto.AnnouncementRequest;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.EventRequest;
import edu.mec.studentportal.client.dto.EventStatus;
import edu.mec.studentportal.client.dto.FeedbackDto;
import edu.mec.studentportal.client.dto.FeedbackSummaryDto;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/**
 * Organizer dashboard: manage own proposals (create/edit/resubmit, cancel approved events),
 * view rosters, publish announcements and read feedback for own events.
 */
public final class OrganizerDashboard extends DashboardPanel {
    private static final TypeReference<List<EventDto>> EVENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<AnnouncementDto>> ANNOUNCEMENT_LIST =
        new TypeReference<>() {};
    private static final TypeReference<List<FeedbackDto>> FEEDBACK_LIST = new TypeReference<>() {};

    private final DefaultTableModel eventsModel = UiSupport.tableModel(
        "ID", "Title", "Venue", "Starts", "Ends", "Cap", "Reg", "Avail", "Status"
    );
    private final JTable eventsTable = new JTable(eventsModel);
    private List<EventDto> eventRows = List.of();

    private final OrganizerRegistrationRosterPanel rosterPanel;

    private final DefaultTableModel announcementsModel = UiSupport.tableModel(
        "ID", "Title", "Audience", "Status", "Created"
    );
    private final JTable announcementsTable = new JTable(announcementsModel);
    private List<AnnouncementDto> announcementRows = List.of();

    private final JComboBox<EventDto> feedbackEvent = new JComboBox<>();
    private final DefaultTableModel feedbackModel = UiSupport.tableModel(
        "ID", "Student", "Rating", "Comments", "Submitted"
    );
    private final JTextArea feedbackSummaryArea = new JTextArea(3, 32);

    public OrganizerDashboard(ApiClient api, SessionContext session) {
        super("Organizer Dashboard", api, session);
        rosterPanel = new OrganizerRegistrationRosterPanel(api, session);
        eventsTable.setAutoCreateRowSorter(true);
        eventsTable.setFillsViewportHeight(true);
        tabs.addTab("My events", buildEventsTab());
        tabs.addTab("Rosters", buildRosterTab());
        tabs.addTab("Announcements", buildAnnouncementsTab());
        tabs.addTab("Event feedback", buildFeedbackTab());
    }

    @Override
    public void refreshAll() {
        refreshEvents();
        refreshAnnouncements();
    }

    private JPanel buildEventsTab() {
        JButton create = new JButton("Create proposal");
        create.addActionListener(e -> createEvent(create));
        JButton edit = new JButton("Edit / resubmit");
        edit.addActionListener(e -> editSelected(edit));
        JButton cancel = new JButton("Cancel event");
        cancel.addActionListener(e -> cancelSelected(cancel));
        JButton details = new JButton("Details");
        details.addActionListener(e -> showDetails());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshEvents());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(create);
        buttons.add(edit);
        buttons.add(cancel);
        buttons.add(details);
        buttons.add(refresh);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(eventsTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRosterTab() {
        return rosterPanel;
    }

    private JPanel buildAnnouncementsTab() {
        JButton post = new JButton("Post announcement");
        post.addActionListener(e -> postAnnouncement(post));
        JButton archive = new JButton("Archive");
        archive.addActionListener(e -> archiveSelected(archive));
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

    private JPanel buildFeedbackTab() {
        feedbackSummaryArea.setEditable(false);
        feedbackSummaryArea.setLineWrap(true);
        JButton load = new JButton("Load feedback");
        load.addActionListener(e -> loadFeedback(load));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(new javax.swing.JLabel("Event:"));
        buttons.add(feedbackEvent);
        buttons.add(load);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(feedbackModel)), BorderLayout.CENTER);
        panel.add(new JScrollPane(feedbackSummaryArea), BorderLayout.SOUTH);
        return panel;
    }

    private void refreshEvents() {
        status("Loading events…");
        ApiTask.run(
            () -> api.get("/events", EVENT_LIST),
            rows -> {
                eventRows = rows == null ? List.of() : rows;
                UiSupport.clear(eventsModel);
                for (EventDto event : eventRows) {
                    UiSupport.addRow(
                        eventsModel,
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
                rosterPanel.setEvents(eventRows);
                updateFeedbackEvents();
                status("Events: " + eventRows.size());
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

    private void updateFeedbackEvents() {
        feedbackEvent.removeAllItems();
        for (EventDto event : eventRows) {
            if (event.status() == EventStatus.APPROVED
                || event.status() == EventStatus.CANCELLED) {
                feedbackEvent.addItem(event);
            }
        }
    }

    private void createEvent(JButton trigger) {
        String[] labels = {
            "Title", "Description", "Venue", "Start time (yyyy-MM-ddTHH:mm)",
            "End time (yyyy-MM-ddTHH:mm)", "Capacity"
        };
        String[] initial = { "", "", "", "", "", "50" };
        String[] values = UiSupport.form(this, "Create event proposal", labels, initial);
        if (values == null) {
            return;
        }
        if (values[0].isEmpty() || values[1].isEmpty() || values[2].isEmpty()
            || values[3].isEmpty() || values[4].isEmpty()) {
            UiSupport.info(this, "Title, description, venue and times are required.");
            return;
        }
        int capacity;
        try {
            capacity = Integer.parseInt(values[5]);
        } catch (NumberFormatException exception) {
            UiSupport.info(this, "Capacity must be a number.");
            return;
        }
        EventRequest request = new EventRequest(
            values[0], values[1], values[2],
            LocalDateTime.parse(values[3]), LocalDateTime.parse(values[4]), capacity
        );
        status("Creating proposal…");
        ApiTask.run(
            () -> api.post("/events", request, EventDto.class),
            created -> {
                status("Proposal created.");
                UiSupport.info(this, "Proposal #" + created.id() + " submitted for approval.");
                refreshEvents();
            },
            this::error,
            trigger
        );
    }

    private void editSelected(JButton trigger) {
        EventDto event = selectedEvent();
        if (event == null) {
            status("Select an event first.");
            return;
        }
        String[] labels = {
            "Title", "Description", "Venue", "Start time (yyyy-MM-ddTHH:mm)",
            "End time (yyyy-MM-ddTHH:mm)", "Capacity"
        };
        String[] initial = {
            event.title(), event.description(), event.venue(),
            event.startTime() == null ? "" : event.startTime().toString(),
            event.endTime() == null ? "" : event.endTime().toString(),
            String.valueOf(event.capacity())
        };
        String[] values = UiSupport.form(this, "Edit proposal #" + event.id(), labels, initial);
        if (values == null) {
            return;
        }
        EventRequest request = new EventRequest(
            values[0], values[1], values[2],
            LocalDateTime.parse(values[3]), LocalDateTime.parse(values[4]),
            Integer.parseInt(values[5])
        );
        status("Saving proposal…");
        ApiTask.run(
            () -> api.put("/events/" + event.id(), request, EventDto.class),
            updated -> {
                status("Proposal updated.");
                UiSupport.info(this, "Proposal #" + updated.id() + " resubmitted.");
                refreshEvents();
            },
            this::error,
            trigger
        );
    }

    private void cancelSelected(JButton trigger) {
        EventDto event = selectedEvent();
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
            cancelled -> {
                status("Event cancelled.");
                refreshEvents();
            },
            this::error,
            trigger
        );
    }

    private void showDetails() {
        EventDto event = selectedEvent();
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
        rows.put("Registered", String.valueOf(event.registeredCount()));
        rows.put("Available", String.valueOf(event.availableSlots()));
        if (event.reviewReason() != null) {
            rows.put("Review reason", event.reviewReason());
        }
        UiSupport.details(this, "Event #" + event.id(), rows);
    }

    private void postAnnouncement(JButton trigger) {
        String[] labels = { "Title", "Content", "Audience (blank=Everyone)" };
        String[] values = UiSupport.form(this, "Post announcement", labels,
            new String[] { "", "", "" });
        if (values == null) {
            return;
        }
        if (values[0].isEmpty() || values[1].isEmpty()) {
            UiSupport.info(this, "Title and content are required.");
            return;
        }
        String audience = values[2].isEmpty() ? null : values[2];
        status("Publishing announcement…");
        ApiTask.run(
            () -> api.post("/announcements", new AnnouncementRequest(values[0], values[1],
                audience), AnnouncementDto.class),
            created -> {
                status("Announcement published.");
                refreshAnnouncements();
            },
            this::error,
            trigger
        );
    }

    private void archiveSelected(JButton trigger) {
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

    private void loadFeedback(JButton trigger) {
        EventDto event = (EventDto) feedbackEvent.getSelectedItem();
        if (event == null) {
            status("No event selected.");
            return;
        }
        status("Loading feedback…");
        ApiTask.run(
            () -> {
                List<FeedbackDto> list = api.get(
                    "/events/" + event.id() + "/feedback", FEEDBACK_LIST);
                FeedbackSummaryDto summary = null;
                try {
                    summary = api.get(
                        "/events/" + event.id() + "/feedback/summary", FeedbackSummaryDto.class);
                } catch (edu.mec.studentportal.client.api.ApiException ignored) {
                    // summary optional; list still shown
                }
                return new Object[] { list, summary };
            },
            result -> {
                @SuppressWarnings("unchecked")
                List<FeedbackDto> list = (List<FeedbackDto>) result[0];
                FeedbackSummaryDto summary = (FeedbackSummaryDto) result[1];
                UiSupport.clear(feedbackModel);
                if (list != null) {
                    for (FeedbackDto feedback : list) {
                        UiSupport.addRow(
                            feedbackModel,
                            feedback.id(),
                            feedback.studentId(),
                            feedback.rating(),
                            feedback.comments(),
                            UiSupport.dateTime(feedback.submittedAt())
                        );
                    }
                }
                feedbackSummaryArea.setText(summary == null
                    ? "No feedback yet."
                    : "Average: " + summary.averageRating()
                        + "  ·  " + summary.ratingCount() + " rating(s)");
                status("Feedback rows: " + (list == null ? 0 : list.size()));
            },
            this::error,
            trigger
        );
    }

    private EventDto selectedEvent() {
        int row = eventsTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        long id = ((Number) eventsModel.getValueAt(
            eventsTable.convertRowIndexToModel(row), 0)).longValue();
        for (EventDto event : eventRows) {
            if (event.id() == id) {
                return event;
            }
        }
        return null;
    }
}
