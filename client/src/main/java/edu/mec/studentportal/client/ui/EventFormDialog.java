package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.ui.theme.PortalTheme;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/**
 * Create/edit-event form dialog: title, description, venue dropdown (fed from the
 * admin-managed venue list), date + time pickers for start/end, and capacity.
 * Returns {@code {title, description, venue, startISO, endISO, capacity}} or null
 * when cancelled — same shape as the old text-only form, so callers parse as before.
 */
public final class EventFormDialog {
    private EventFormDialog() {}

    public static String[] show(Component parent, String title, String[] initial,
        List<String> venues, boolean venuesEditable) {
        JTextField titleField = new JTextField(valueAt(initial, 0), 28);
        JTextField descriptionField = new JTextField(valueAt(initial, 1), 28);
        JComboBox<String> venueCombo = new JComboBox<>(
            (venues == null ? List.<String>of() : venues).toArray(String[]::new));
        String initialVenue = valueAt(initial, 2);
        if (!initialVenue.isEmpty()
            && venueCombo.getItemCount() > 0
            && venueCombo.getSelectedIndex() < 0) {
            // keep a historical venue selectable even after admins removed it
            venueCombo.addItem(initialVenue);
        }
        if (!initialVenue.isEmpty() && venueCombo.getItemCount() == 0) {
            venueCombo.addItem(initialVenue);
        }
        venueCombo.setEditable(venuesEditable || venueCombo.getItemCount() == 0);
        if (!initialVenue.isEmpty()) {
            venueCombo.setSelectedItem(initialVenue);
        }
        venueCombo.setPreferredSize(new Dimension(220, 30));

        DateField startDate = new DateField();
        TimeField startTime = new TimeField();
        DateField endDate = new DateField();
        TimeField endTime = new TimeField();
        startDate.setText(valueAt(initial, 3).substring(0,
            Math.min(10, valueAt(initial, 3).length())));
        startTime.setText(valueAt(initial, 3).length() > 11
            ? valueAt(initial, 3).substring(11) : "");
        endDate.setText(valueAt(initial, 4).substring(0,
            Math.min(10, valueAt(initial, 4).length())));
        endTime.setText(valueAt(initial, 4).length() > 11
            ? valueAt(initial, 4).substring(11) : "");
        JTextField capacityField = new JTextField(valueAt(initial, 5), 8);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(6, 6, 6, 6));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 6, 5, 6);
        constraints.anchor = GridBagConstraints.WEST;

        addRow(form, constraints, 0, "Title", titleField);
        addRow(form, constraints, 1, "Description", descriptionField);
        addRow(form, constraints, 2, "Venue", venueCombo);

        JPanel startPanel = pair(startDate, startTime);
        JPanel endPanel = pair(endDate, endTime);
        addRow(form, constraints, 3, "Starts", startPanel);
        addRow(form, constraints, 4, "Ends", endPanel);
        addRow(form, constraints, 5, "Capacity", capacityField);

        JLabel hint = new JLabel("<html><span style='color:" + hex(PortalTheme.MUTED)
            + "'>Dates are yyyy-MM-dd · times snap to 15-minute steps.</span></html>");
        hint.setFont(PortalTheme.XS);
        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(hint, constraints);

        UiKit.enableTextEditing(titleField);
        UiKit.enableTextEditing(descriptionField);
        UiKit.enableTextEditing(capacityField);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(PortalTheme.CARD);
        content.add(form, BorderLayout.CENTER);

        // start must be chosen first: the end controls explain that and send the
        // user back to the start fields instead of opening.
        String startFirst = "Select the start date and time first.";
        java.util.function.BooleanSupplier startMissing =
            () -> combine(startDate, startTime) == null;
        Runnable sendToStart = () -> {
            JOptionPane.showMessageDialog(parent, startFirst, title,
                JOptionPane.INFORMATION_MESSAGE);
            javax.swing.SwingUtilities.invokeLater(startDate::requestInputFocus);
        };
        endDate.setSelectionBlocked(startMissing, sendToStart);
        endTime.setSelectionBlocked(startMissing, sendToStart);
        endDate.setPickValidator(date -> {
            LocalDate startDay = startDate.getDate();
            if (startDay != null && date.isBefore(startDay)) {
                return "End date must be after the start date.";
            }
            return null;
        });

        while (true) {
            int result = JOptionPane.showConfirmDialog(parent, content, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }
            String venue = venueValue(venueCombo);
            LocalDateTime start = combine(startDate, startTime);
            LocalDateTime end = combine(endDate, endTime);
            String problem = validate(titleField.getText(), descriptionField.getText(),
                venue, start, end, capacityField.getText());
            if (problem == null) {
                return new String[] {
                    titleField.getText().trim(),
                    descriptionField.getText().trim(),
                    venue,
                    start.toString(),
                    end.toString(),
                    capacityField.getText().trim()
                };
            }
            JOptionPane.showMessageDialog(parent, problem, title,
                JOptionPane.WARNING_MESSAGE);
        }
    }

    private static String validate(String eventTitle, String description, String venue,
        LocalDateTime start, LocalDateTime end, String capacity) {
        if (eventTitle == null || eventTitle.isBlank()) {
            return "Title is required.";
        }
        if (description == null || description.isBlank()) {
            return "Description is required.";
        }
        if (venue == null || venue.isBlank()) {
            return "Venue is required — pick one from the list or type it.";
        }
        if (start == null) {
            return "Start date and time are required.";
        }
        if (end == null) {
            return "End date and time are required.";
        }
        if (!end.isAfter(start)) {
            return "End must be after start.";
        }
        try {
            int parsed = Integer.parseInt(capacity.trim());
            if (parsed < 1) {
                return "Capacity must be at least 1.";
            }
        } catch (NumberFormatException exception) {
            return "Capacity must be a number.";
        }
        return null;
    }

    private static LocalDateTime combine(DateField date, TimeField time) {
        LocalDate day = date.getDate();
        LocalTime hour = time.getTime();
        if (day == null || hour == null) {
            return null;
        }
        return LocalDateTime.of(day, hour);
    }

    private static String venueValue(JComboBox<String> combo) {
        Object item = combo.isEditable() ? combo.getEditor().getItem()
            : combo.getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }

    private static String valueAt(String[] initial, int index) {
        if (initial == null || index >= initial.length || initial[index] == null) {
            return "";
        }
        return initial[index];
    }

    private static JPanel pair(Component left, Component right) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        panel.add(left);
        panel.add(right);
        return panel;
    }

    private static void addRow(JPanel form, GridBagConstraints constraints, int row,
        String label, Component field) {
        constraints.gridy = row;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        JLabel caption = new JLabel(label);
        caption.setFont(PortalTheme.SM);
        caption.setForeground(PortalTheme.INK);
        form.add(caption, constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        form.add(field, constraints);
    }

    private static String hex(java.awt.Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(),
            color.getBlue());
    }
}
