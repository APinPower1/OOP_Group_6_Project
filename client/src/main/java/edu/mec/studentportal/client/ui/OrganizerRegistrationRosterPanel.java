package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.RosterDto;
import edu.mec.studentportal.client.dto.RosterEntry;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

final class OrganizerRegistrationRosterPanel extends JPanel {
    private final ApiClient api;
    private final SessionContext session;
    private final JComboBox<EventDto> events = new JComboBox<>();
    private final JButton loadButton = new JButton("Load roster");
    private final JLabel status = new JLabel("Select one of your events to view its roster.");
    private final JLabel summary = new JLabel(" ");
    private final DefaultTableModel rosterModel = UiSupport.tableModel(
        "Registration ID", "Student ID", "Full name", "Roll number", "Status",
        "Ticket number", "Registered at"
    );
    private final JTable rosterTable = new JTable(rosterModel);
    private boolean loading;

    OrganizerRegistrationRosterPanel(ApiClient api, SessionContext session) {
        this.api = api;
        this.session = session;
        setLayout(new BorderLayout(0, 8));

        events.setName("organizer.roster.events");
        events.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EventDto event) {
                    setText(event.title() + " (#" + event.id() + ")");
                }
                return this;
            }
        });
        events.addActionListener(event -> selectionChanged());

        loadButton.setName("organizer.roster.load");
        loadButton.setEnabled(false);
        loadButton.addActionListener(event -> loadRoster());

        rosterTable.setName("organizer.roster.table");
        rosterTable.setFillsViewportHeight(true);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Event:"));
        controls.add(events);
        controls.add(loadButton);

        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.add(controls, BorderLayout.NORTH);
        heading.add(status, BorderLayout.CENTER);
        heading.add(summary, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);
        add(new JScrollPane(rosterTable), BorderLayout.CENTER);
    }

    void setEvents(List<EventDto> eventRows) {
        events.removeAllItems();
        if (!isOrganizer()) {
            UiSupport.clear(rosterModel);
            summary.setText(" ");
            status.setText("Organizer access is required to view registration rosters.");
            updateControls();
            return;
        }

        long organizerId = session.getUser().id();
        for (EventDto event : eventRows == null ? List.<EventDto>of() : eventRows) {
            if (event.createdBy() == organizerId) {
                events.addItem(event);
            }
        }
        UiSupport.clear(rosterModel);
        summary.setText(" ");
        status.setText(events.getItemCount() == 0
            ? "No events owned by this Organizer are available."
            : "Select one of your events to view its roster.");
        updateControls();
    }

    private void selectionChanged() {
        if (loading) {
            return;
        }
        UiSupport.clear(rosterModel);
        summary.setText(" ");
        status.setText(events.getSelectedItem() == null
            ? "No events owned by this Organizer are available."
            : "Select Load roster to view this event's registrations.");
        loadButton.setText("Load roster");
        updateControls();
    }

    private void loadRoster() {
        EventDto event = selectedOwnedEvent();
        if (loading || event == null) {
            return;
        }

        loading = true;
        UiSupport.clear(rosterModel);
        summary.setText(" ");
        status.setText("Loading registration roster...");
        events.setEnabled(false);
        loadButton.setEnabled(false);
        loadButton.setText("Loading...");

        ApiTask.run(
            () -> api.get("/events/" + event.id() + "/registrations", RosterDto.class),
            roster -> {
                if (!isStillSelected(event.id())) {
                    finishLoading("The selected event changed. Select an event and try again.");
                    return;
                }
                displayRoster(roster);
                finishLoading(roster.registrations().isEmpty()
                    ? "No registrations yet."
                    : roster.registrations().size() + " registration(s) shown.");
                loadButton.setText("Refresh roster");
            },
            this::showLoadError
        );
    }

    private void displayRoster(RosterDto roster) {
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
        summary.setText("Seats: " + roster.registeredCount() + "/" + roster.capacity()
            + " registered; " + roster.availableSlots() + " available");
    }

    private void showLoadError(ApiException exception) {
        finishLoading("Could not load roster: " + exception.getMessage());
        loadButton.setText("Retry");
    }

    private void finishLoading(String message) {
        loading = false;
        status.setText(message);
        events.setEnabled(isOrganizer() && events.getItemCount() > 0);
        updateControls();
    }

    private void updateControls() {
        loadButton.setEnabled(!loading && selectedOwnedEvent() != null);
        events.setEnabled(!loading && isOrganizer() && events.getItemCount() > 0);
    }

    private EventDto selectedOwnedEvent() {
        Object selected = events.getSelectedItem();
        if (selected instanceof EventDto event && isOwned(event)) {
            return event;
        }
        return null;
    }

    private boolean isStillSelected(long eventId) {
        EventDto selected = selectedOwnedEvent();
        return selected != null && selected.id() == eventId;
    }

    private boolean isOwned(EventDto event) {
        return isOrganizer() && event.createdBy() == session.getUser().id();
    }

    private boolean isOrganizer() {
        return session.getRole() == Role.ORGANIZER && session.getUser() != null;
    }

    JButton loadButton() {
        return loadButton;
    }

    JComboBox<EventDto> eventSelector() {
        return events;
    }

    JTable rosterTable() {
        return rosterTable;
    }

    JLabel statusLabel() {
        return status;
    }

    JLabel summaryLabel() {
        return summary;
    }
}
