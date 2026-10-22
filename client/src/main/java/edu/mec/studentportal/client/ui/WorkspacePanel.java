package edu.mec.studentportal.client.ui;

import com.fasterxml.jackson.databind.JsonNode;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiException;
import edu.mec.studentportal.client.auth.SessionContext;
import edu.mec.studentportal.client.auth.SessionContext.UserSummary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

final class WorkspacePanel extends JPanel {
    private static final DateTimeFormatter DISPLAY_TIME =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final ApiClient apiClient;
    private final SessionContext session;
    private final Runnable signOut;
    private final Runnable sessionExpired;
    private final JPanel content = new JPanel(new BorderLayout(0, 18));
    private final JLabel pageTitle = new JLabel();
    private final JLabel pageSubtitle = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private JTable table;
    private List<JsonNode> rows = List.of();
    private String currentSection = "Overview";
    private int pendingRequests;

    WorkspacePanel(
        ApiClient apiClient,
        SessionContext session,
        Runnable signOut,
        Runnable sessionExpired
    ) {
        this.apiClient = apiClient;
        this.session = session;
        this.signOut = signOut;
        this.sessionExpired = sessionExpired;
        setLayout(new BorderLayout());
        setBackground(PortalTheme.CANVAS);
        add(createSidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        showSection("Overview");
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setBackground(PortalTheme.NAVY);
        sidebar.setBorder(BorderFactory.createEmptyBorder(25, 16, 18, 16));

        JPanel top = new JPanel(new BorderLayout(0, 7));
        top.setOpaque(false);
        JLabel brand = new JLabel("MEC  /  PORTAL");
        brand.setForeground(Color.WHITE);
        brand.setFont(new Font("Segoe UI", Font.BOLD, 17));
        JLabel role = new JLabel(session.user().role() + " WORKSPACE");
        role.setForeground(new Color(167, 187, 214));
        role.setFont(new Font("Segoe UI", Font.BOLD, 10));
        top.add(brand, BorderLayout.NORTH);
        top.add(role, BorderLayout.CENTER);

        JPanel navigation = new JPanel(new GridBagLayout());
        navigation.setOpaque(false);
        String[] items = navigationItems(session.user().role());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = new Insets(5, 0, 5, 0);
        for (String item : items) {
            JButton navButton = new JButton(item);
            navButton.setHorizontalAlignment(JButton.LEFT);
            navButton.setForeground(Color.WHITE);
            navButton.setFont(PortalTheme.BODY);
            navButton.setBackground(PortalTheme.NAVY);
            navButton.setFocusPainted(false);
            navButton.setBorder(BorderFactory.createEmptyBorder(11, 13, 11, 10));
            navButton.addActionListener(event -> showSection(item));
            constraints.gridy++;
            navigation.add(navButton, constraints);
        }
        JPanel navigationWrap = new JPanel(new BorderLayout());
        navigationWrap.setOpaque(false);
        navigationWrap.setBorder(BorderFactory.createEmptyBorder(32, 0, 0, 0));
        navigationWrap.add(navigation, BorderLayout.NORTH);
        sidebar.add(top, BorderLayout.NORTH);
        sidebar.add(navigationWrap, BorderLayout.CENTER);

        JPanel userArea = new JPanel(new BorderLayout(0, 9));
        userArea.setOpaque(false);
        JLabel name = new JLabel(session.user().fullName());
        name.setForeground(Color.WHITE);
        name.setFont(PortalTheme.LABEL);
        JLabel email = new JLabel(session.user().email());
        email.setForeground(new Color(167, 187, 214));
        email.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JButton signOutButton = new JButton("Sign out");
        signOutButton.setHorizontalAlignment(JButton.LEFT);
        signOutButton.setForeground(new Color(218, 227, 239));
        signOutButton.setBackground(PortalTheme.NAVY_LIGHT);
        signOutButton.setFocusPainted(false);
        signOutButton.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        signOutButton.addActionListener(event -> signOut.run());
        JPanel identity = new JPanel(new BorderLayout(0, 4));
        identity.setOpaque(false);
        identity.add(name, BorderLayout.NORTH);
        identity.add(email, BorderLayout.CENTER);
        userArea.add(identity, BorderLayout.NORTH);
        userArea.add(signOutButton, BorderLayout.SOUTH);
        sidebar.add(userArea, BorderLayout.SOUTH);
        return sidebar;
    }

    private static String[] navigationItems(String role) {
        return switch (role) {
            case "STUDENT" -> new String[] {
                "Overview", "Events", "My registrations", "Clubs", "Announcements", "Notifications"
            };
            case "ORGANIZER" -> new String[] {
                "Overview", "Events", "Clubs", "Announcements", "Notifications", "Feedback"
            };
            case "FACULTY" -> new String[] {
                "Overview", "Events", "Clubs", "Announcements", "Notifications", "Feedback"
            };
            case "ADMIN" -> new String[] {
                "Overview", "Events", "Clubs", "Announcements", "Notifications", "Users", "Feedback"
            };
            default -> new String[] {"Overview", "Events", "Announcements", "Notifications"};
        };
    }

    private void showSection(String section) {
        currentSection = section;
        content.removeAll();
        content.setBackground(PortalTheme.CANVAS);
        content.setBorder(BorderFactory.createEmptyBorder(28, 32, 26, 32));
        pageTitle.setText(section);
        pageTitle.setFont(PortalTheme.TITLE);
        pageTitle.setForeground(PortalTheme.INK);
        pageSubtitle.setText(subtitle(section));
        pageSubtitle.setFont(PortalTheme.BODY);
        pageSubtitle.setForeground(PortalTheme.MUTED);

        JPanel heading = new JPanel(new BorderLayout(0, 7));
        heading.setOpaque(false);
        heading.add(pageTitle, BorderLayout.NORTH);
        heading.add(pageSubtitle, BorderLayout.CENTER);
        actions.removeAll();
        actions.setOpaque(false);
        addActions(section);
        setToolbarEnabled(pendingRequests == 0);
        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(actions, BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);
        status.setForeground(PortalTheme.MUTED);
        status.setText(" ");
        status.setFont(PortalTheme.BODY);
        content.add(status, BorderLayout.SOUTH);
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(PortalTheme.WHITE);
        center.setBorder(BorderFactory.createLineBorder(PortalTheme.BORDER));
        content.add(center, BorderLayout.CENTER);
        revalidate();
        repaint();
        loadSection(section, center);
    }

    private static String subtitle(String section) {
        return switch (section) {
            case "Overview" -> "Your campus activity and the latest portal updates.";
            case "Events" -> "Discover events, manage proposals, and review event activity.";
            case "My registrations" -> "Your event reservations and ticket details.";
            case "Clubs" -> "Explore campus communities and manage club memberships.";
            case "Announcements" -> "Campus updates published for your audience.";
            case "Notifications" -> "Your in-app updates and event alerts.";
            case "Users" -> "Create accounts and manage access to the student portal.";
            case "Feedback" -> "Review event feedback and participation ratings.";
            default -> "";
        };
    }

    private void addActions(String section) {
        addButton("Refresh", PortalTheme.NAVY_LIGHT, () -> showSection(currentSection));
        String role = session.user().role();
        if (section.equals("Events")) {
            if (role.equals("ORGANIZER")) {
                addButton("Propose event", PortalTheme.BLUE, this::createEvent);
                addButton("Edit proposal", PortalTheme.NAVY_LIGHT, this::editEvent);
            }
            if (role.equals("FACULTY") || role.equals("ADMIN")) {
                addButton("Review pending", PortalTheme.BLUE, () -> loadPendingEvents());
                addButton("Approve", PortalTheme.GREEN, this::approveEvent);
                addButton("Reject", PortalTheme.RED, this::rejectEvent);
            }
            if (role.equals("ORGANIZER") || role.equals("FACULTY") || role.equals("ADMIN")) {
                addButton("View roster", PortalTheme.NAVY_LIGHT, this::viewRoster);
                addButton("View feedback", PortalTheme.NAVY_LIGHT, this::viewSelectedFeedback);
                addButton("Cancel event", PortalTheme.RED, this::cancelEvent);
            }
            if (role.equals("STUDENT")) {
                addButton("Register", PortalTheme.BLUE, this::registerForEvent);
                addButton("Leave event", PortalTheme.RED, this::cancelRegistration);
                addButton("Submit feedback", PortalTheme.NAVY_LIGHT, this::submitFeedback);
            }
        } else if (section.equals("My registrations")) {
            addButton("Cancel registration", PortalTheme.RED, this::cancelRegistration);
        } else if (section.equals("Clubs")) {
            if (role.equals("STUDENT")) {
                addButton("Join club", PortalTheme.BLUE, () -> changeMembership(true));
                addButton("Leave club", PortalTheme.NAVY_LIGHT, () -> changeMembership(false));
            } else if (role.equals("ADMIN")) {
                addButton("Create club", PortalTheme.BLUE, this::createClub);
                addButton("Edit club", PortalTheme.NAVY_LIGHT, this::editClub);
                addButton("View members", PortalTheme.NAVY_LIGHT, this::viewClubMembers);
                addButton("Deactivate club", PortalTheme.RED, this::deactivateClub);
            }
        } else if (section.equals("Announcements")) {
            if (role.equals("ORGANIZER") || role.equals("ADMIN")) {
                addButton("Publish announcement", PortalTheme.BLUE, this::publishAnnouncement);
                addButton("Archive", PortalTheme.NAVY_LIGHT, this::archiveAnnouncement);
            }
        } else if (section.equals("Notifications")) {
            addButton("Mark as read", PortalTheme.BLUE, this::markNotificationRead);
        } else if (section.equals("Users") && role.equals("ADMIN")) {
            addButton("Create account", PortalTheme.BLUE, this::createUser);
            addButton("Edit account", PortalTheme.NAVY_LIGHT, this::editUser);
            addButton("Enable / disable", PortalTheme.NAVY_LIGHT, this::toggleUser);
        } else if (section.equals("Feedback")) {
            addButton("View selected event", PortalTheme.BLUE, this::viewSelectedFeedback);
        }
    }

    private void addButton(String text, Color color, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 13, 9, 13));
        button.addActionListener(event -> action.run());
        actions.add(button);
    }

    private void loadSection(String section, JPanel container) {
        status.setText("Loading " + section.toLowerCase() + "...");
        container.removeAll();
        JLabel loading = new JLabel("Loading...");
        loading.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        container.add(loading, BorderLayout.NORTH);
        String endpoint = switch (section) {
            case "Events" -> "events";
            case "My registrations" -> "registrations/me";
            case "Clubs" -> "clubs";
            case "Announcements" -> "announcements";
            case "Notifications" -> "notifications/me";
            case "Users" -> "admin/users?page=0&size=100";
            case "Feedback" -> "events";
            default -> "events";
        };
        runRequest(() -> apiClient.get(endpoint), data -> {
            if (section.equals("Overview")) {
                showOverview(container, data);
            } else if (section.equals("Users")) {
                showTable(container, array(data.path("items")), columnsFor(section));
            } else if (section.equals("Feedback")) {
                showTable(container, array(data), columnsFor("Events"));
            } else {
                showTable(container, array(data), columnsFor(section));
            }
            status.setText(section.equals("Overview")
                ? "Connected to the student portal service."
                : rows.size() + (rows.size() == 1 ? " record" : " records"));
        }, container);
    }

    private void loadPendingEvents() {
        JPanel center = currentCenter();
        status.setText("Loading pending proposals...");
        runRequest(() -> apiClient.get("events?status=PENDING_APPROVAL"), data -> {
            showTable(center, array(data), columnsFor("Events"));
            status.setText(rows.size() + " pending proposal(s)");
        }, center);
    }

    private void showOverview(JPanel container, JsonNode data) {
        List<JsonNode> events = array(data);
        int upcoming = 0;
        int pending = 0;
        int approved = 0;
        for (JsonNode event : events) {
            String eventStatus = event.path("status").asText();
            if (eventStatus.equals("PENDING_APPROVAL")) pending++;
            if (eventStatus.equals("APPROVED")) approved++;
            if (eventStatus.equals("APPROVED")
                && LocalDateTime.parse(event.path("startTime").asText()).isAfter(LocalDateTime.now())) {
                upcoming++;
            }
        }
        JPanel dashboard = new JPanel(new BorderLayout(0, 20));
        dashboard.setBackground(PortalTheme.WHITE);
        dashboard.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        JPanel metrics = new JPanel(new GridBagLayout());
        metrics.setOpaque(false);
        String[] titles = {"Visible events", "Upcoming", "Pending review"};
        int[] values = {events.size(), upcoming, pending};
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 12, 12);
        for (int index = 0; index < titles.length; index++) {
            constraints.gridx = index;
            metrics.add(metricCard(titles[index], values[index]), constraints);
        }
        JLabel sectionLabel = new JLabel("Approved events");
        sectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        sectionLabel.setForeground(PortalTheme.INK);
        JPanel lower = new JPanel(new BorderLayout(0, 12));
        lower.setOpaque(false);
        lower.add(sectionLabel, BorderLayout.NORTH);
        showTable(lower, events.stream()
            .filter(event -> event.path("status").asText().equals("APPROVED"))
            .toList(), columnsFor("Events"));
        dashboard.add(metrics, BorderLayout.NORTH);
        dashboard.add(lower, BorderLayout.CENTER);
        container.removeAll();
        container.add(dashboard, BorderLayout.CENTER);
        container.revalidate();
        container.repaint();
        if (approved == 0) {
            status.setText("No approved events are available yet.");
        }
    }

    private JPanel metricCard(String label, int value) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(PortalTheme.CANVAS);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PortalTheme.BORDER),
            BorderFactory.createEmptyBorder(18, 17, 18, 17)
        ));
        JLabel metric = new JLabel(Integer.toString(value));
        metric.setFont(new Font("Segoe UI", Font.BOLD, 27));
        metric.setForeground(PortalTheme.BLUE);
        JLabel caption = new JLabel(label);
        caption.setFont(PortalTheme.BODY);
        caption.setForeground(PortalTheme.MUTED);
        card.add(metric, BorderLayout.NORTH);
        card.add(caption, BorderLayout.CENTER);
        return card;
    }

    private void showTable(JPanel container, List<JsonNode> data, String[] columns) {
        rows = List.copyOf(data);
        Object[][] values = new Object[rows.size()][columns.length];
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            JsonNode row = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < columns.length; colIndex++) {
                values[rowIndex][colIndex] = displayValue(row, columns[colIndex]);
            }
        }
        DefaultTableModel model = new DefaultTableModel(values, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setRowHeight(38);
        table.setFont(PortalTheme.BODY);
        table.setForeground(PortalTheme.INK);
        table.setGridColor(PortalTheme.BORDER);
        table.setSelectionBackground(new Color(226, 236, 253));
        table.setSelectionForeground(PortalTheme.INK);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setForeground(PortalTheme.MUTED);
        table.getTableHeader().setBackground(PortalTheme.CANVAS);
        table.setAutoCreateRowSorter(true);

        container.removeAll();
        if (rows.isEmpty()) {
            JLabel empty = new JLabel("Nothing to show here yet.");
            empty.setForeground(PortalTheme.MUTED);
            empty.setFont(PortalTheme.BODY);
            empty.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            container.add(empty, BorderLayout.NORTH);
        } else {
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            container.add(scrollPane, BorderLayout.CENTER);
        }
        container.revalidate();
        container.repaint();
    }

    private static String[] columnsFor(String section) {
        return switch (section) {
            case "Events" -> new String[] {
                "id", "title", "venue", "startTime", "status", "registeredCount", "capacity"
            };
            case "My registrations" -> new String[] {
                "eventId", "status", "ticketNo", "registeredAt"
            };
            case "Clubs" -> new String[] {
                "id", "name", "category", "activeMemberCount", "active"
            };
            case "Announcements" -> new String[] {
                "id", "title", "audienceRole", "status", "publishedAt"
            };
            case "Notifications" -> new String[] {
                "id", "title", "notificationType", "createdAt", "readAt"
            };
            case "Users" -> new String[] {
                "id", "fullName", "email", "department", "role", "enabled"
            };
            default -> new String[0];
        };
    }

    private static String displayValue(JsonNode row, String field) {
        JsonNode value = row.path(field);
        if (value.isMissingNode() || value.isNull()) return "—";
        if ((field.equals("startTime") || field.equals("endTime")
            || field.equals("registeredAt") || field.equals("createdAt")
            || field.equals("publishedAt")) && value.isTextual()) {
            try {
                return LocalDateTime.parse(value.asText()).format(DISPLAY_TIME);
            } catch (RuntimeException ignored) {
                return value.asText();
            }
        }
        return value.isContainerNode() ? value.toString() : value.asText();
    }

    private void createEvent() {
        JTextField title = field("Title");
        JTextArea description = area(4);
        JTextField venue = field("Venue");
        JTextField start = field("Start (yyyy-MM-ddTHH:mm:ss)");
        JTextField end = field("End (yyyy-MM-ddTHH:mm:ss)");
        JTextField capacity = field("Capacity");
        JPanel form = formPanel(
            "Title", title, "Description", description, "Venue", venue,
            "Start time", start, "End time", end, "Capacity", capacity
        );
        if (!confirmForm("Propose an event", form)) return;
        try {
            Map<String, Object> body = Map.of(
                "title", required(title, "Title"),
                "description", required(description, "Description"),
                "venue", required(venue, "Venue"),
                "startTime", LocalDateTime.parse(required(start, "Start time")).toString(),
                "endTime", LocalDateTime.parse(required(end, "End time")).toString(),
                "capacity", Integer.parseInt(required(capacity, "Capacity"))
            );
            perform(() -> apiClient.post("events", body), "Event proposal submitted.");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void registerForEvent() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        perform(() -> apiClient.post("events/" + selected.path("id").asLong() + "/registrations", null),
            "Registration confirmed.");
    }

    private void editEvent() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        JTextField title = field("Title");
        title.setText(selected.path("title").asText());
        JTextArea description = area(4);
        description.setText(selected.path("description").asText());
        JTextField venue = field("Venue");
        venue.setText(selected.path("venue").asText());
        JTextField start = field("Start (yyyy-MM-ddTHH:mm:ss)");
        start.setText(selected.path("startTime").asText());
        JTextField end = field("End (yyyy-MM-ddTHH:mm:ss)");
        end.setText(selected.path("endTime").asText());
        JTextField capacity = field("Capacity");
        capacity.setText(selected.path("capacity").asText());
        JPanel form = formPanel(
            "Title", title, "Description", description, "Venue", venue,
            "Start time", start, "End time", end, "Capacity", capacity
        );
        if (!confirmForm("Edit event proposal", form)) return;
        try {
            Map<String, Object> body = Map.of(
                "title", required(title, "Title"),
                "description", required(description, "Description"),
                "venue", required(venue, "Venue"),
                "startTime", LocalDateTime.parse(required(start, "Start time")).toString(),
                "endTime", LocalDateTime.parse(required(end, "End time")).toString(),
                "capacity", Integer.parseInt(required(capacity, "Capacity"))
            );
            perform(
                () -> apiClient.put("events/" + selected.path("id").asLong(), body),
                "Event proposal resubmitted."
            );
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void cancelRegistration() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        String idField = currentSection.equals("My registrations") ? "eventId" : "id";
        long eventId = selected.path(idField).asLong();
        perform(() -> apiClient.delete("events/" + eventId + "/registrations/me"),
            "Registration cancelled.");
    }

    private void cancelEvent() {
        JsonNode selected = selectedRow();
        if (selected == null || !confirm("Cancel this event and notify registered students?")) return;
        perform(() -> apiClient.post("events/" + selected.path("id").asLong() + "/cancel", null),
            "Event cancelled.");
    }

    private void approveEvent() {
        JsonNode selected = selectedRow();
        if (selected == null || !confirm("Approve this event proposal?")) return;
        perform(() -> apiClient.post("events/" + selected.path("id").asLong() + "/approve", null),
            "Event approved.");
    }

    private void rejectEvent() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        String reason = JOptionPane.showInputDialog(this, "Reason for rejection:", "Reject event",
            JOptionPane.QUESTION_MESSAGE);
        if (reason == null) return;
        if (reason.isBlank()) {
            showError("A rejection reason is required.");
            return;
        }
        perform(() -> apiClient.post(
            "events/" + selected.path("id").asLong() + "/reject",
            Map.of("reason", reason.trim())
        ), "Event proposal rejected.");
    }

    private void submitFeedback() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        JTextField rating = field("Rating (1-5)");
        JTextArea comments = area(4);
        if (!confirmForm("Share event feedback", formPanel("Rating", rating, "Comments", comments))) return;
        try {
            int score = Integer.parseInt(required(rating, "Rating"));
            if (score < 1 || score > 5) throw new IllegalArgumentException("Rating must be between 1 and 5.");
            perform(() -> apiClient.post(
                "events/" + selected.path("id").asLong() + "/feedback",
                Map.of("rating", score, "comments", comments.getText().trim())
            ), "Thank you. Your feedback has been submitted.");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void createClub() {
        JTextField name = field("Name");
        JTextField category = field("Category");
        JTextArea description = area(4);
        if (!confirmForm("Create a club", formPanel(
            "Name", name, "Category", category, "Description", description
        ))) return;
        perform(() -> apiClient.post("clubs", Map.of(
            "name", required(name, "Name"),
            "category", required(category, "Category"),
            "description", required(description, "Description")
        )), "Club created.");
    }

    private void editClub() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        JTextField name = field("Name");
        name.setText(selected.path("name").asText());
        JTextField category = field("Category");
        category.setText(selected.path("category").asText());
        JTextArea description = area(4);
        description.setText(selected.path("description").asText());
        JPanel form = formPanel("Name", name, "Category", category, "Description", description);
        if (!confirmForm("Edit club", form)) return;
        try {
            Map<String, Object> body = Map.of(
                "name", required(name, "Name"),
                "category", required(category, "Category"),
                "description", required(description, "Description")
            );
            perform(
                () -> apiClient.put("clubs/" + selected.path("id").asLong(), body),
                "Club updated."
            );
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void changeMembership(boolean join) {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        long clubId = selected.path("id").asLong();
        perform(() -> join
            ? apiClient.post("clubs/" + clubId + "/membership", null)
            : apiClient.delete("clubs/" + clubId + "/membership"),
            join ? "Club membership activated." : "Club membership ended.");
    }

    private void deactivateClub() {
        JsonNode selected = selectedRow();
        if (selected == null || !confirm("Deactivate this club? Membership history will be retained.")) return;
        perform(() -> apiClient.post("clubs/" + selected.path("id").asLong() + "/deactivate", null),
            "Club deactivated.");
    }

    private void viewClubMembers() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        runRequest(() -> apiClient.get("clubs/" + selected.path("id").asLong() + "/members"), members -> {
            StringBuilder message = new StringBuilder();
            for (JsonNode member : array(members)) {
                message.append(member.path("fullName").asText())
                    .append("  |  ").append(member.path("rollNumber").asText())
                    .append("  |  ").append(member.path("status").asText()).append('\n');
            }
            if (message.isEmpty()) message.append("No club members to display.");
            showMessage(message.toString(), "Club members");
        }, currentCenter());
    }

    private void publishAnnouncement() {
        JTextField title = field("Title");
        JTextArea contentField = area(5);
        JComboBox<String> audience = new JComboBox<>(
            new String[] {"All users", "STUDENT", "ORGANIZER", "FACULTY", "ADMIN"}
        );
        JPanel form = formPanel("Title", title, "Message", contentField, "Audience", audience);
        if (!confirmForm("Publish an announcement", form)) return;
        String selectedAudience = (String) audience.getSelectedItem();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", required(title, "Title"));
        body.put("content", required(contentField, "Message"));
        body.put("audienceRole", "All users".equals(selectedAudience) ? null : selectedAudience);
        perform(() -> apiClient.post("announcements", body), "Announcement published.");
    }

    private void archiveAnnouncement() {
        JsonNode selected = selectedRow();
        if (selected == null || !confirm("Archive this announcement?")) return;
        perform(() -> apiClient.post(
            "announcements/" + selected.path("id").asLong() + "/archive",
            null
        ), "Announcement archived.");
    }

    private void markNotificationRead() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        perform(() -> apiClient.patch(
            "notifications/" + selected.path("id").asLong() + "/read",
            null
        ), "Notification marked as read.");
    }

    private void viewRoster() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        runRequest(() -> apiClient.get("events/" + selected.path("id").asLong() + "/registrations"),
            roster -> {
                List<JsonNode> entries = array(roster.path("registrations"));
                StringBuilder message = new StringBuilder("Confirmed: ")
                    .append(roster.path("registeredCount").asInt())
                    .append(" / ").append(roster.path("capacity").asInt())
                    .append("\n\n");
                for (JsonNode entry : entries) {
                    message.append(entry.path("fullName").asText())
                        .append("  |  ").append(entry.path("rollNumber").asText())
                        .append("  |  ").append(entry.path("ticketNo").asText()).append('\n');
                }
                showMessage(message.toString(), "Event roster");
            }, currentCenter());
    }

    private void viewSelectedFeedback() {
        JsonNode selected = selectedRow();
        if (selected == null) {
            showMessage("Select an event first, then use View selected event.", "Feedback");
            return;
        }
        long eventId = selected.path("id").asLong();
        runRequest(() -> apiClient.get("events/" + eventId + "/feedback"), feedback -> {
            List<JsonNode> entries = array(feedback);
            StringBuilder message = new StringBuilder();
            double totalRating = 0;
            for (JsonNode entry : entries) {
                totalRating += entry.path("rating").asInt();
                message.append("Rating: ").append(entry.path("rating").asInt()).append("/5")
                    .append("  |  ")
                    .append(entry.path("submittedAt").asText())
                    .append('\n')
                    .append(entry.path("comments").asText("No comment"))
                    .append("\n\n");
            }
            if (entries.isEmpty()) {
                message.append("No feedback has been submitted for this event.");
            } else {
                message.insert(0, String.format(
                    "Average rating: %.1f/5  |  %d response(s)%n%n",
                    totalRating / entries.size(),
                    entries.size()
                ));
            }
            showMessage(message.toString(), "Event feedback");
        }, currentCenter());
    }

    private void createUser() {
        JTextField fullName = field("Full name");
        JTextField email = field("Email");
        JTextField department = field("Department");
        JPasswordField password = new JPasswordField();
        password.setToolTipText("Initial password (8-72 UTF-8 bytes)");
        JComboBox<String> role = new JComboBox<>(
            new String[] {"STUDENT", "ORGANIZER", "FACULTY", "ADMIN"}
        );
        JTextField profileFirst = field("Profile identifier / designation");
        JTextField profileSecond = field("Semester or designation (if applicable)");
        JTextField profileThird = field("Batch (students only)");
        JLabel profileFirstLabel = new JLabel();
        JLabel profileSecondLabel = new JLabel();
        JLabel profileThirdLabel = new JLabel();
        configureProfileFields(
            role, profileFirstLabel, profileSecondLabel, profileThirdLabel,
            profileFirst, profileSecond, profileThird
        );
        JPanel form = formPanel(
            "Full name", fullName, "Email", email, "Department", department,
            "Initial password", password, "Role", role,
            profileFirstLabel, profileFirst, profileSecondLabel, profileSecond,
            profileThirdLabel, profileThird
        );
        if (!confirmForm("Create an account", form)) return;
        try {
            String selectedRole = (String) role.getSelectedItem();
            Map<String, Object> profile = new LinkedHashMap<>();
            switch (selectedRole) {
                case "STUDENT" -> {
                    profile.put("rollNumber", required(profileFirst, "Roll number"));
                    profile.put("semester", Integer.parseInt(required(profileSecond, "Semester")));
                    profile.put("batch", required(profileThird, "Batch"));
                }
                case "ORGANIZER" -> profile.put("designation", required(profileFirst, "Designation"));
                case "FACULTY" -> {
                    profile.put("employeeId", required(profileFirst, "Employee ID"));
                    profile.put("designation", required(profileSecond, "Designation"));
                }
                case "ADMIN" -> profile.put("adminLevel", 1);
                default -> throw new IllegalArgumentException("Select a supported account role.");
            }
            Map<String, Object> body = Map.of(
                "fullName", required(fullName, "Full name"),
                "email", required(email, "Email"),
                "department", required(department, "Department"),
                "password", required(password, "Initial password"),
                "role", selectedRole,
                "profile", profile
            );
            perform(() -> apiClient.post("admin/users", body), "Account created.");
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void editUser() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        JsonNode currentProfile = selected.path("profile");
        JTextField fullName = field("Full name");
        fullName.setText(selected.path("fullName").asText());
        JTextField email = field("Email");
        email.setText(selected.path("email").asText());
        JTextField department = field("Department");
        department.setText(selected.path("department").asText());
        JComboBox<String> role = new JComboBox<>(
            new String[] {"STUDENT", "ORGANIZER", "FACULTY", "ADMIN"}
        );
        role.setSelectedItem(selected.path("role").asText());
        JTextField profileFirst = field("Profile identifier / designation");
        JTextField profileSecond = field("Semester or designation (if applicable)");
        JTextField profileThird = field("Batch (students only)");
        switch (selected.path("role").asText()) {
            case "STUDENT" -> {
                profileFirst.setText(currentProfile.path("rollNumber").asText());
                profileSecond.setText(currentProfile.path("semester").asText());
                profileThird.setText(currentProfile.path("batch").asText());
            }
            case "ORGANIZER" -> profileFirst.setText(currentProfile.path("designation").asText());
            case "FACULTY" -> {
                profileFirst.setText(currentProfile.path("employeeId").asText());
                profileSecond.setText(currentProfile.path("designation").asText());
            }
            case "ADMIN" -> profileFirst.setText(currentProfile.path("adminLevel").asText());
            default -> { }
        }
        JLabel profileFirstLabel = new JLabel();
        JLabel profileSecondLabel = new JLabel();
        JLabel profileThirdLabel = new JLabel();
        configureProfileFields(
            role, profileFirstLabel, profileSecondLabel, profileThirdLabel,
            profileFirst, profileSecond, profileThird
        );
        JPanel form = formPanel(
            "Full name", fullName, "Email", email, "Department", department,
            "Role", role, profileFirstLabel, profileFirst,
            profileSecondLabel, profileSecond, profileThirdLabel, profileThird
        );
        if (!confirmForm("Edit account", form)) return;
        try {
            String selectedRole = (String) role.getSelectedItem();
            Map<String, Object> profile = new LinkedHashMap<>();
            switch (selectedRole) {
                case "STUDENT" -> {
                    profile.put("rollNumber", required(profileFirst, "Roll number"));
                    profile.put("semester", Integer.parseInt(required(profileSecond, "Semester")));
                    profile.put("batch", required(profileThird, "Batch"));
                }
                case "ORGANIZER" -> profile.put("designation", required(profileFirst, "Designation"));
                case "FACULTY" -> {
                    profile.put("employeeId", required(profileFirst, "Employee ID"));
                    profile.put("designation", required(profileSecond, "Designation"));
                }
                case "ADMIN" -> profile.put(
                    "adminLevel",
                    profileFirst.getText().isBlank() ? 1 : Integer.parseInt(profileFirst.getText().trim())
                );
                default -> throw new IllegalArgumentException("Select a supported account role.");
            }
            Map<String, Object> body = Map.of(
                "fullName", required(fullName, "Full name"),
                "email", required(email, "Email"),
                "department", required(department, "Department"),
                "role", selectedRole,
                "profile", profile
            );
            perform(
                () -> apiClient.put("admin/users/" + selected.path("id").asLong(), body),
                "Account updated."
            );
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void toggleUser() {
        JsonNode selected = selectedRow();
        if (selected == null) return;
        long userId = selected.path("id").asLong();
        String action = selected.path("enabled").asBoolean() ? "disable" : "enable";
        if (!confirm(action.equals("disable") ? "Disable this account?" : "Enable this account?")) return;
        perform(() -> apiClient.post("admin/users/" + userId + "/" + action, null),
            "Account " + action + "d.");
    }

    private void perform(ThrowingSupplier<JsonNode> request, String successMessage) {
        status.setText("Saving...");
        runRequest(request, response -> {
            status.setText(successMessage);
            showSection(currentSection);
        }, currentCenter());
    }

    private void runRequest(
        ThrowingSupplier<JsonNode> request,
        Consumer<JsonNode> success,
        JPanel target
    ) {
        pendingRequests++;
        setToolbarEnabled(false);
        new SwingWorker<JsonNode, Void>() {
            @Override
            protected JsonNode doInBackground() throws Exception {
                return request.get();
            }

            @Override
            protected void done() {
                try {
                    success.accept(get());
                } catch (Exception exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof ApiException apiException) {
                        if (apiException.status() == 401) {
                            sessionExpired.run();
                            return;
                        }
                        status.setForeground(PortalTheme.RED);
                        status.setText(apiException.getMessage());
                        showError(apiException.getMessage());
                    } else {
                        status.setForeground(PortalTheme.RED);
                        status.setText("The request could not be completed.");
                        showError("The request could not be completed.");
                    }
                    target.removeAll();
                    JLabel error = new JLabel("Unable to load this section. Refresh to try again.");
                    error.setForeground(PortalTheme.RED);
                    error.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                    target.add(error, BorderLayout.NORTH);
                    target.revalidate();
                    target.repaint();
                } finally {
                    pendingRequests--;
                    setToolbarEnabled(pendingRequests == 0);
                }
            }
        }.execute();
    }

    private void setToolbarEnabled(boolean enabled) {
        for (java.awt.Component component : actions.getComponents()) {
            component.setEnabled(enabled);
        }
    }

    private JsonNode selectedRow() {
        if (table == null || table.getSelectedRow() < 0) {
            showMessage("Select a row first.", "No selection");
            return null;
        }
        int selectedIndex = table.convertRowIndexToModel(table.getSelectedRow());
        if (selectedIndex >= rows.size()) return null;
        return rows.get(selectedIndex);
    }

    private JPanel currentCenter() {
        for (java.awt.Component component : content.getComponents()) {
            if (component instanceof JPanel panel && panel != actions) {
                if (panel.getBorder() instanceof javax.swing.border.LineBorder) return panel;
            }
        }
        return content;
    }

    private static List<JsonNode> array(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(values::add);
        return values;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Portal", JOptionPane.ERROR_MESSAGE);
    }

    private void showMessage(String message, String title) {
        JTextArea text = new JTextArea(message, 15, 60);
        text.setEditable(false);
        text.setFont(PortalTheme.BODY);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(text), title, JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(
            this, message, "Please confirm", JOptionPane.YES_NO_OPTION
        ) == JOptionPane.YES_OPTION;
    }

    private boolean confirmForm(String title, JPanel form) {
        return JOptionPane.showConfirmDialog(
            this, new JScrollPane(form), title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        ) == JOptionPane.OK_OPTION;
    }

    private static JPanel formPanel(Object... fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 8, 6, 8);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.weightx = 0;
        for (int index = 0; index < fields.length; index += 2) {
            constraints.gridy = index;
            JLabel label = fields[index] instanceof JLabel existingLabel
                ? existingLabel
                : new JLabel(fields[index].toString());
            label.setFont(PortalTheme.LABEL);
            panel.add(label, constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            constraints.gridy = index;
            java.awt.Component input = (java.awt.Component) fields[index + 1];
            input.setPreferredSize(new Dimension(360, input instanceof JTextArea ? 90 : 34));
            panel.add(input, constraints);
            constraints.gridx = 0;
            constraints.weightx = 0;
        }
        return panel;
    }

    private static void configureProfileFields(
        JComboBox<String> role,
        JLabel firstLabel,
        JLabel secondLabel,
        JLabel thirdLabel,
        JTextField first,
        JTextField second,
        JTextField third
    ) {
        String[] currentRole = {(String) role.getSelectedItem()};
        Runnable updateFields = () -> {
            String selectedRole = (String) role.getSelectedItem();
            if (!selectedRole.equals(currentRole[0])) {
                first.setText("");
                second.setText("");
                third.setText("");
                currentRole[0] = selectedRole;
            }
            String[] labels = switch (selectedRole) {
                case "STUDENT" -> new String[] {"Roll number", "Semester", "Batch"};
                case "ORGANIZER" -> new String[] {"Designation", "", ""};
                case "FACULTY" -> new String[] {"Employee ID", "Designation", ""};
                case "ADMIN" -> new String[] {"Admin level", "", ""};
                default -> throw new IllegalArgumentException("Select a supported account role.");
            };
            JLabel[] profileLabels = {firstLabel, secondLabel, thirdLabel};
            JTextField[] profileFields = {first, second, third};
            for (int index = 0; index < labels.length; index++) {
                boolean visible = !labels[index].isEmpty();
                profileLabels[index].setText(labels[index]);
                profileLabels[index].setEnabled(visible);
                profileFields[index].setEnabled(visible);
            }
        };
        role.addActionListener(event -> updateFields.run());
        updateFields.run();
    }

    private static JTextField field(String placeholder) {
        JTextField field = new JTextField();
        field.setToolTipText(placeholder);
        return field;
    }

    private static JTextArea area(int rows) {
        JTextArea area = new JTextArea(rows, 30);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static String required(JTextField field, String label) {
        return required(field.getText(), label);
    }

    private static String required(JPasswordField field, String label) {
        return required(new String(field.getPassword()), label);
    }

    private static String required(JTextArea field, String label) {
        return required(field.getText(), label);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
