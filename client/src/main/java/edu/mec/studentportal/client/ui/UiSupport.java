package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiException;
import java.awt.Component;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/**
 * Small shared Swing helpers: error/info dialogs with contract-aware messages, simple form
 * dialogs, non-editable table models and selection helpers. No SQL or authorization logic
 * lives here — the backend is the source of truth.
 */
public final class UiSupport {
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private UiSupport() {}

    public static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static void showError(Component parent, ApiException exception) {
        String message;
        if (exception.isNetworkError()) {
            message = exception.getMessage();
        } else if (exception.isUnauthorized()) {
            message = "Your session has ended. Please sign in again.";
        } else if (exception.isForbidden()) {
            message = "Access denied. " + safeMessage(exception);
        } else {
            message = safeMessage(exception);
        }
        JOptionPane.showMessageDialog(parent, message, "Request failed",
            JOptionPane.ERROR_MESSAGE);
    }

    public static void showFailure(Component parent, Throwable failure) {
        if (failure instanceof ApiException apiException) {
            showError(parent, apiException);
            return;
        }
        String message = failure.getMessage() == null || failure.getMessage().isBlank()
            ? "Unexpected error."
            : failure.getMessage();
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Student Portal",
            JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(
            parent, message, "Confirm", JOptionPane.YES_NO_OPTION
        ) == JOptionPane.YES_OPTION;
    }

    /** Prompt dialog returning trimmed values in order, or null when cancelled. */
    public static String[] form(Component parent, String title, String[] labels,
        String[] initialValues) {
        JPanel panel = new JPanel(new GridLayout(0, 1));
        javax.swing.JTextField[] fields = new javax.swing.JTextField[labels.length];
        for (int i = 0; i < labels.length; i++) {
            panel.add(new JLabel(labels[i]));
            fields[i] = new javax.swing.JTextField(
                initialValues == null || initialValues[i] == null ? "" : initialValues[i]
            );
            panel.add(fields[i]);
        }
        int result = JOptionPane.showConfirmDialog(
            parent, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        String[] values = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            values[i] = fields[i].getText().trim();
        }
        return values;
    }

    public static void details(Component parent, String title, Map<String, String> rows) {
        JTextArea area = new JTextArea(12, 48);
        area.setEditable(false);
        for (Map.Entry<String, String> row : rows.entrySet()) {
            area.append(row.getKey() + ": " + row.getValue() + "\n");
        }
        JScrollPane scroll = new JScrollPane(area);
        JOptionPane.showMessageDialog(parent, scroll, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static Map<String, String> rows() {
        return new LinkedHashMap<>();
    }

    /** Selected row's value in the given column, or null when nothing valid is selected. */
    public static Long selectedId(JTable table, int idColumn) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(row);
        Object value = table.getModel().getValueAt(modelRow, idColumn);
        if (value instanceof Long id) {
            return id;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static void addRow(DefaultTableModel model, Object... values) {
        model.addRow(values);
    }

    public static void clear(DefaultTableModel model) {
        model.setRowCount(0);
    }

    public static JComponent labeled(String label, JComponent component) {
        JPanel panel = new JPanel(new java.awt.BorderLayout(6, 0));
        panel.add(new JLabel(label), java.awt.BorderLayout.WEST);
        panel.add(component, java.awt.BorderLayout.CENTER);
        return panel;
    }

    private static String safeMessage(ApiException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Request failed (" + exception.getCode() + ").";
        }
        return message;
    }
}
