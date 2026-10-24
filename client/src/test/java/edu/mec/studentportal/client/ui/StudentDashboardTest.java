package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.SessionContext;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Proxy;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class StudentDashboardTest {
    @Test
    void eventSearchAndAvailabilityFiltersUpdateVisibleRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ApiClient api = (ApiClient) Proxy.newProxyInstance(
                ApiClient.class.getClassLoader(),
                new Class<?>[] { ApiClient.class },
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException("Unexpected API request");
                }
            );
            StudentDashboard dashboard = new StudentDashboard(api, new SessionContext());
            JTable table = (JTable) findByName(dashboard, "events.table");
            JTextField search = (JTextField) findByName(dashboard, "events.search");
            JComboBox<?> availability =
                (JComboBox<?>) findByName(dashboard, "events.availability");
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.addRow(new Object[] { 1L, "Robotics Night", "Auditorium",
                "2026-11-01", "2026-11-01", 100, 80, 20, "APPROVED" });
            model.addRow(new Object[] { 2L, "Music Meetup", "Studio",
                "2026-11-02", "2026-11-02", 30, 30, 0, "APPROVED" });

            search.setText("robotics");
            assertEquals(1, table.getRowCount());
            assertEquals("Robotics Night", table.getValueAt(0, 1));

            search.setText("");
            availability.setSelectedItem("Fully booked");
            assertEquals(1, table.getRowCount());
            assertEquals("Music Meetup", table.getValueAt(0, 1));
        });
    }

    private static Component findByName(Container parent, String name) {
        Component match = find(parent, name);
        if (match != null) {
            return match;
        }
        throw new AssertionError("Component not found: " + name);
    }

    private static Component find(Container parent, String name) {
        if (name.equals(parent.getName())) {
            return parent;
        }
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container nested) {
                Component match = find(nested, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
