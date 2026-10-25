package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.ui.theme.UiKit;
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
import edu.mec.studentportal.client.dto.RosterDto;
import edu.mec.studentportal.client.dto.RosterEntry;
import edu.mec.studentportal.client.dto.VenueResponse;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JOptionPane;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/**
 * Organizer dashboard: manage own proposals (create/edit/resubmit, cancel approved events),
 * view rosters, publish announcements and read feedback for own events.
 */
public final class OrganizerDashboard extends DashboardPanel {
    private static final TypeReference<List<EventDto>> EVENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<VenueResponse>> VENUE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<AnnouncementDto>> ANNOUNCEMENT_LIST =
        new TypeReference<>() {};
    private static final TypeReference<List<FeedbackDto>> FEEDBACK_LIST = new TypeReference<>() {};

    private final DefaultTableModel eventsModel = UiSupport.tableModel(
        "ID", "Title", "Venue", "Starts", "Ends", "Cap", "Reg", "Avail", "Status"
    );
    private final JTable eventsTable = new JTable(eventsModel);
    private List<EventDto> eventRows = List.of();

    private final JComboBox<EventDto> rosterEvent = new JComboBox<>();
    private final DefaultTableModel rosterModel = UiSupport.tableModel(
        "Reg ID", "Student ID", "Name", "Roll", "Status", "Ticket", "Registered"
    );

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
        reloadEventCombos();
    }

    private JPanel buildEventsTab() {
        JButton create = UiKit.ghostButton("Create proposal");
        create.addActionListener(e -> createEvent(create));
        JButton edit = UiKit.ghostButton("Edit / resubmit");
        edit.addActionListener(e -> editSelected(edit));
        JButton cancel = UiKit.ghostButton("Cancel event");
        cancel.addActionListener(e -> cancelSelected(cancel));
        JButton details = UiKit.ghostButton("Details");
        details.addActionListener(e -> showDetails());
        JButton refresh = UiKit.ghostButton("Refresh");
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
        JButton load = UiKit.ghostButton("Load roster");
        load.addActionListener(e -> loadRoster(load));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(new javax.swing.JLabel("Event:"));
        buttons.add(rosterEvent);
        buttons.add(load);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(rosterModel)), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAnnouncementsTab() {
        JButton post = UiKit.ghostButton("Post announcement");
        post.addActionListener(e -> postAnnouncement(post));
        JButton archive = UiKit.ghostButton("Archive");
        archive.addActionListener(e -> archiveSelected(archive));
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshAnnouncements());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
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

    private JPanel buildFeedbackTab() {
        feedbackSummaryArea.setEditable(false);
        feedbackSummaryArea.setLineWrap(true);
        JButton load = UiKit.ghostButton("Load feedback");
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

    private void reloadEventCombos() {
        ApiTask.run(
            () -> api.get("/events", EVENT_LIST),
            rows -> {
                eventRows = rows == null ? List.of() : rows;
                rosterEvent.removeAllItems();
                feedbackEvent.removeAllItems();
                for (EventDto event : eventRows) {
                    if (event.status() != EventStatus.REJECTED
                        && event.status() != EventStatus.CANCELLED) {
                        rosterEvent.addItem(event);
                    }
                    if (event.status() == EventStatus.APPROVED
                        || event.status() == EventStatus.CANCELLED) {
                        feedbackEvent.addItem(event);
                    }
                }
            },
            this::error
        );
    }

    private void createEvent(JButton trigger) {
        withVenues(trigger, null, "Create event proposal", values -> {
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
                    UiSupport.info(this,
                        "Proposal #" + created.id() + " submitted for approval.");
                    refreshEvents();
                },
                this::error,
                trigger
            );
        });
    }

    private void editSelected(JButton trigger) {
        EventDto event = selectedEvent();
        if (event == null) {
            status("Select an event first.");
            return;
        }
        withVenues(trigger, event, "Edit proposal #" + event.id(), values -> {
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
        });
    }

    /**
     * Loads the admin-managed venue list, then opens the event form dialog. If the
     * list cannot be fetched (or is empty) the venue combo falls back to free text so
     * event creation never blocks on the venue service.
     */
    private void withVenues(JButton trigger, EventDto event, String title,
        java.util.function.Consumer<String[]> onFormValues) {
        status("Loading venues…");
        ApiTask.run(
            () -> api.get("/venues", VENUE_LIST),
            venues -> {
                List<String> names = venues == null ? List.of() : venues.stream()
                    .map(VenueResponse::name)
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
                status(names.isEmpty()
                    ? "No managed venues — type a venue."
                    : "Venues: " + names.size() + " available.");
                openEventForm(event, title, names, names.isEmpty(), onFormValues);
            },
            failure -> {
                status("Venue list unavailable — you can type a venue.");
                openEventForm(event, title, List.of(), true, onFormValues);
            },
            trigger
        );
    }

    private void openEventForm(EventDto event, String title, List<String> venues,
        boolean venuesEditable, java.util.function.Consumer<String[]> onFormValues) {
        String[] initial;
        if (event == null) {
            initial = new String[] { "", "", "", "", "", "50" };
        } else {
            initial = new String[] {
                event.title(), event.description(), event.venue(),
                event.startTime() == null ? "" : event.startTime().toString(),
                event.endTime() == null ? "" : event.endTime().toString(),
                String.valueOf(event.capacity())
            };
        }
        String[] values = EventFormDialog.show(this, title, initial, venues,
            venuesEditable);
        if (values != null) {
            onFormValues.accept(values);
        }
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

    private void loadRoster(JButton trigger) {
        EventDto event = (EventDto) rosterEvent.getSelectedItem();
        if (event == null) {
            status("Create or refresh events first.");
            return;
        }
        status("Loading roster…");
        ApiTask.run(
            () -> api.get("/events/" + event.id() + "/registrations", RosterDto.class),
            roster -> {
                UiSupport.clear(rosterModel);
                if (roster != null && roster.registrations() != null) {
                    for (RosterEntry entry : roster.registrations()) {
                        UiSupport.addRow(
                            rosterModel,
                            entry.registrationId(),
                            entry.studentId(),
                            entry.fullName(),
                            entry.rollNumber(),
                            entry.status() == null ? "" : entry.status().name(),
                            entry.ticketNo(),
                            UiSupport.dateTime(entry.registeredAt())
                        );
                    }
                    status("Seats: " + roster.registeredCount() + "/" + roster.capacity()
                        + " · " + roster.registrations().size() + " registrations");
                }
            },
            this::error,
            trigger
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
        String[] audienceOptions = {"ALL", "STUDENTS", "FACULTY", "ORGANIZERS", "ADMINS"};
        JComboBox<String> audienceBox = new JComboBox<>(audienceOptions);
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
        int result = JOptionPane.showConfirmDialog(this, main, "Post Announcement", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
            () -> api.post("/announcements", new AnnouncementRequest(title, content,
                audience), AnnouncementDto.class),
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
