package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.ui.theme.UiKit;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.AnnouncementDto;
import edu.mec.studentportal.client.dto.AnnouncementRequest;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.RejectRequest;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/**
 * Faculty dashboard: review proposal queue with approve/reject (mandatory reason),
 * post campus announcements, and role-appropriate visibility of moderation details.
 */
public final class FacultyDashboard extends DashboardPanel {
    private static final TypeReference<List<EventDto>> EVENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<AnnouncementDto>> ANNOUNCEMENT_LIST =
        new TypeReference<>() {};

    private final DefaultTableModel model = UiSupport.tableModel(
        "ID", "Title", "Organizer ID", "Venue", "Starts", "Ends", "Cap", "Status", "Reason"
    );
    private final JTable table = new JTable(model);
    private final JComboBox<String> statusFilter = new JComboBox<>(
        new String[] { "PENDING_APPROVAL", "ALL", "APPROVED", "REJECTED", "CANCELLED" }
    );
    private List<EventDto> rows = List.of();

    private final DefaultTableModel announcementsModel = UiSupport.tableModel(
        "ID", "Title", "Audience", "Status", "Created"
    );
    private final JTable announcementsTable = new JTable(announcementsModel);
    private List<AnnouncementDto> announcementRows = List.of();

    public FacultyDashboard(ApiClient api, SessionContext session) {
        super("Faculty Dashboard", api, session);
        table.setAutoCreateRowSorter(true);
        tabs.addTab("Review queue", buildQueueTab());
        tabs.addTab("Announcements", buildAnnouncementsTab());
        tabs.setSelectedIndex(0);
    }

    @Override
    public void refreshAll() {
        refreshQueue();
        refreshAnnouncements();
    }

    private JPanel buildQueueTab() {
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshQueue());
        JButton details = UiKit.ghostButton("Details");
        details.addActionListener(e -> showDetails());
        JButton approve = UiKit.ghostButton("Approve");
        approve.addActionListener(e -> approveSelected(approve));
        JButton reject = UiKit.ghostButton("Reject…");
        reject.addActionListener(e -> rejectSelected(reject));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(new javax.swing.JLabel("Status:"));
        buttons.add(statusFilter);
        buttons.add(refresh);
        buttons.add(details);
        buttons.add(approve);
        buttons.add(reject);
        statusFilter.addActionListener(e -> refreshQueue());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void refreshQueue() {
        String filter = String.valueOf(statusFilter.getSelectedItem());
        String path = "ALL".equals(filter) ? "/events" : "/events?status=" + filter;
        status("Loading events…");
        ApiTask.run(
            () -> api.get(path, EVENT_LIST),
            loaded -> {
                rows = loaded == null ? List.of() : loaded;
                UiSupport.clear(model);
                for (EventDto event : rows) {
                    UiSupport.addRow(
                        model,
                        event.id(),
                        event.title(),
                        event.createdBy(),
                        event.venue(),
                        UiSupport.dateTime(event.startTime()),
                        UiSupport.dateTime(event.endTime()),
                        event.capacity(),
                        event.status() == null ? "" : event.status().name(),
                        event.reviewReason() == null ? "" : event.reviewReason()
                    );
                }
                status("Events: " + rows.size());
            },
            this::error
        );
    }

    private JPanel buildAnnouncementsTab() {
        JButton post = UiKit.ghostButton("Post announcement");
        post.addActionListener(e -> postAnnouncement(post));
        JButton archive = UiKit.ghostButton("Archive");
        archive.addActionListener(e -> archiveAnnouncement(archive));
        JButton refresh = UiKit.ghostButton("Refresh");
        refresh.addActionListener(e -> refreshAnnouncements());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(post);
        buttons.add(archive);
        buttons.add(refresh);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(announcementsTable), BorderLayout.CENTER);
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
        JPanel main = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets = new java.awt.Insets(4, 4, 4, 4);
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Title"), gbc);
        gbc.gridx = 1;
        javax.swing.JTextField titleField = new javax.swing.JTextField(28);
        UiKit.enableTextEditing(titleField);
        form.add(titleField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        form.add(new JLabel("Audience"), gbc);
        gbc.gridx = 1;
        JComboBox<String> audienceBox = new JComboBox<>(
            new String[] {"ALL", "STUDENT", "FACULTY", "ORGANIZER", "ADMIN"});
        form.add(audienceBox, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = java.awt.GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Content"), gbc);
        gbc.gridx = 1;
        gbc.fill = java.awt.GridBagConstraints.BOTH;
        JTextArea contentArea = new JTextArea(8, 28);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        UiKit.enableTextEditing(contentArea);
        JScrollPane contentScroll = new JScrollPane(contentArea);
        contentScroll.setPreferredSize(new java.awt.Dimension(340, 160));
        form.add(contentScroll, gbc);
        main.add(form, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, main, "Post announcement",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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

    private void showAnnouncementDetails() {
        int row = announcementsTable.getSelectedRow();
        if (row < 0 || announcementRows == null) {
            return;
        }
        int modelRow = announcementsTable.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= announcementRows.size()) {
            return;
        }
        AnnouncementDto announcement = announcementRows.get(modelRow);
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JLabel title = new JLabel(announcement.title() == null ? "" : announcement.title());
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        panel.add(title, BorderLayout.NORTH);
        JTextArea content = new JTextArea(
            announcement.content() == null ? "" : announcement.content());
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setBackground(panel.getBackground());
        JScrollPane scroll = new JScrollPane(content);
        scroll.setPreferredSize(new java.awt.Dimension(400, 200));
        panel.add(scroll, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, panel, "Announcement",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void approveSelected(JButton trigger) {
        EventDto event = selected();
        if (event == null) {
            status("Select an event first.");
            return;
        }
        if (!UiSupport.confirm(this, "Approve proposal #" + event.id() + " — " + event.title() + "?")) {
            return;
        }
        status("Approving…");
        ApiTask.run(
            () -> api.post("/events/" + event.id() + "/approve", null, EventDto.class),
            approved -> {
                status("Approved.");
                refreshQueue();
            },
            this::error,
            trigger
        );
    }

    private void rejectSelected(JButton trigger) {
        EventDto event = selected();
        if (event == null) {
            status("Select an event first.");
            return;
        }
        String reason = javax.swing.JOptionPane.showInputDialog(
            this,
            "Rejection reason (required):",
            "Reject proposal #" + event.id(),
            javax.swing.JOptionPane.PLAIN_MESSAGE
        );
        if (reason == null || reason.isBlank()) {
            status("Rejection cancelled — a reason is required.");
            return;
        }
        status("Rejecting…");
        ApiTask.run(
            () -> api.post("/events/" + event.id() + "/reject", new RejectRequest(reason.trim()),
                EventDto.class),
            rejected -> {
                status("Rejected.");
                refreshQueue();
            },
            this::error,
            trigger
        );
    }

    private void showDetails() {
        EventDto event = selected();
        if (event == null) {
            status("Select an event first.");
            return;
        }
        Map<String, String> detail = UiSupport.rows();
        detail.put("Title", event.title());
        detail.put("Description", event.description());
        detail.put("Venue", event.venue());
        detail.put("Starts", UiSupport.dateTime(event.startTime()));
        detail.put("Ends", UiSupport.dateTime(event.endTime()));
        detail.put("Capacity", String.valueOf(event.capacity()));
        detail.put("Status", String.valueOf(event.status()));
        detail.put("Created by (organizer id)", String.valueOf(event.createdBy()));
        detail.put("Registered", String.valueOf(event.registeredCount()));
        if (event.reviewReason() != null && !event.reviewReason().isBlank()) {
            detail.put("Review reason", event.reviewReason());
        }
        UiSupport.details(this, "Proposal #" + event.id(), detail);
    }

    private EventDto selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        long id = ((Number) model.getValueAt(table.convertRowIndexToModel(row), 0)).longValue();
        for (EventDto event : rows) {
            if (event.id() == id) {
                return event;
            }
        }
        return null;
    }
}
