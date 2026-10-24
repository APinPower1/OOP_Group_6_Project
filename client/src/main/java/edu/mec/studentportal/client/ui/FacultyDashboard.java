package edu.mec.studentportal.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.RejectRequest;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Faculty dashboard: review proposal queue with approve/reject (mandatory reason) and
 * role-appropriate visibility of event moderation details.
 */
public final class FacultyDashboard extends DashboardPanel {
    private static final TypeReference<List<EventDto>> EVENT_LIST = new TypeReference<>() {};

    private final DefaultTableModel model = UiSupport.tableModel(
        "ID", "Title", "Organizer ID", "Venue", "Starts", "Ends", "Cap", "Status", "Reason"
    );
    private final JTable table = UiSupport.configureTable(new JTable(model));
    private final JComboBox<String> statusFilter = new JComboBox<>(
        new String[] { "PENDING_APPROVAL", "ALL", "APPROVED", "REJECTED", "CANCELLED" }
    );
    private List<EventDto> rows = List.of();

    public FacultyDashboard(ApiClient api, SessionContext session) {
        super("Faculty Dashboard", api, session);
        tabs.addTab("Review queue", buildQueueTab());
        tabs.setSelectedIndex(0);
    }

    @Override
    public void refreshAll() {
        refreshQueue();
    }

    private JPanel buildQueueTab() {
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshQueue());
        JButton details = new JButton("Details");
        details.addActionListener(e -> showDetails());
        JButton approve = new JButton("Approve");
        approve.addActionListener(e -> approveSelected(approve));
        JButton reject = new JButton("Reject…");
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
