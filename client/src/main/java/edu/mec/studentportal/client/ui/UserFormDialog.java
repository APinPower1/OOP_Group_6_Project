package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.dto.Role;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Modal form for creating or fully updating an admin user, with role-specific profile fields
 * (D-012/D-016: profile keys must match the role exactly; password only on create).
 */
final class UserFormDialog {
    record Result(
        String fullName,
        String email,
        String password,
        String department,
        Role role,
        Map<String, Object> profile
    ) {}

    private final JPanel panel = new JPanel(new BorderLayout(8, 8));
    private final JTextField fullName = new JTextField(28);
    private final JTextField email = new JTextField(28);
    private final JTextField department = new JTextField(28);
    private final JTextField password = new JTextField(28);
    private final JComboBox<Role> role = new JComboBox<>(Role.values());
    private final CardLayout profileCards = new CardLayout();
    private final JPanel profilePanel = new JPanel(profileCards);

    private final JTextField studentRoll = new JTextField();
    private final JTextField studentSemester = new JTextField(6);
    private final JTextField studentBatch = new JTextField();
    private final JTextField organizerDesignation = new JTextField();
    private final JTextField facultyEmployeeId = new JTextField();
    private final JTextField facultyDesignation = new JTextField();
    private final JTextField adminLevel = new JTextField("1");

    private UserFormDialog(boolean create) {
        JPanel identity = new JPanel(new GridLayout(0, 2, 6, 4));
        identity.add(new JLabel("Full name"));
        identity.add(fullName);
        identity.add(new JLabel("Email"));
        identity.add(email);
        identity.add(new JLabel("Department"));
        identity.add(department);
        if (create) {
            identity.add(new JLabel("Initial password (8–72 chars)"));
            identity.add(password);
        }
        identity.add(new JLabel("Role"));
        identity.add(role);

        profilePanel.add(profileRow("Roll number", studentRoll,
            "Semester", studentSemester,
            "Batch", studentBatch), "STUDENT");
        profilePanel.add(profileRow("Designation", organizerDesignation), "ORGANIZER");
        profilePanel.add(profileRow("Employee ID", facultyEmployeeId,
            "Designation", facultyDesignation), "FACULTY");
        profilePanel.add(profileRow("Admin level", adminLevel), "ADMIN");

        JPanel north = new JPanel(new BorderLayout());
        north.add(identity, BorderLayout.NORTH);
        JPanel profileBox = new JPanel(new BorderLayout(6, 4));
        profileBox.add(new JLabel("Role profile"), BorderLayout.NORTH);
        profileBox.add(profilePanel, BorderLayout.CENTER);
        north.add(profileBox, BorderLayout.SOUTH);

        panel.add(north, BorderLayout.NORTH);
        role.addActionListener(e ->
            profileCards.show(profilePanel, String.valueOf(role.getSelectedItem())));
        profileCards.show(profilePanel, "STUDENT");
    }

    /** Returns the parsed result, or null when cancelled. */
    static Result show(Component parent, boolean create, String title,
        String initialFullName, String initialEmail, String initialDepartment,
        Role initialRole, Map<String, Object> initialProfile) {
        UserFormDialog dialog = new UserFormDialog(create);
        if (initialFullName != null) {
            dialog.fullName.setText(initialFullName);
        }
        if (initialEmail != null) {
            dialog.email.setText(initialEmail);
        }
        if (initialDepartment != null) {
            dialog.department.setText(initialDepartment);
        }
        if (initialRole != null) {
            dialog.role.setSelectedItem(initialRole);
        }
        dialog.prefillProfile(initialRole, initialProfile);
        dialog.profileCards.show(dialog.profilePanel, String.valueOf(
            dialog.role.getSelectedItem()));

        int option = JOptionPane.showConfirmDialog(
            parent, dialog.panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );
        if (option != JOptionPane.OK_OPTION) {
            return null;
        }
        Role selectedRole = (Role) dialog.role.getSelectedItem();
        String pwd = create ? dialog.password.getText() : null;
        return new Result(
            dialog.fullName.getText().trim(),
            dialog.email.getText().trim(),
            pwd == null ? null : pwd,
            dialog.department.getText().trim(),
            selectedRole,
            dialog.profileFor(selectedRole)
        );
    }

    private void prefillProfile(Role selectedRole, Map<String, Object> profile) {
        if (selectedRole == null || profile == null) {
            return;
        }
        switch (selectedRole) {
            case STUDENT -> {
                studentRoll.setText(str(profile.get("rollNumber")));
                studentSemester.setText(str(profile.get("semester")));
                studentBatch.setText(str(profile.get("batch")));
            }
            case ORGANIZER -> organizerDesignation.setText(str(profile.get("designation")));
            case FACULTY -> {
                facultyEmployeeId.setText(str(profile.get("employeeId")));
                facultyDesignation.setText(str(profile.get("designation")));
            }
            case ADMIN -> adminLevel.setText(str(
                profile.getOrDefault("adminLevel", "1")));
        }
    }

    private Map<String, Object> profileFor(Role selectedRole) {
        Map<String, Object> profile = new LinkedHashMap<>();
        switch (selectedRole) {
            case STUDENT -> {
                profile.put("rollNumber", studentRoll.getText().trim());
                profile.put("semester", parsePositiveInt(studentSemester.getText(), 1));
                profile.put("batch", studentBatch.getText().trim());
            }
            case ORGANIZER -> profile.put("designation", organizerDesignation.getText().trim());
            case FACULTY -> {
                profile.put("employeeId", facultyEmployeeId.getText().trim());
                profile.put("designation", facultyDesignation.getText().trim());
            }
            case ADMIN -> profile.put("adminLevel", parsePositiveInt(adminLevel.getText(), 1));
        }
        return profile;
    }

    private static JPanel profileRow(String label1, javax.swing.JTextField field1) {
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.add(new JLabel(label1));
        row.add(field1);
        return row;
    }

    private static JPanel profileRow(String label1, javax.swing.JTextField field1,
        String label2, javax.swing.JTextField field2) {
        JPanel left = profileRow(label1, field1);
        JPanel right = profileRow(label2, field2);
        JPanel wrapper = new JPanel(new GridLayout(1, 2, 8, 0));
        wrapper.add(left);
        wrapper.add(right);
        return wrapper;
    }

    private static JPanel profileRow(String label1, javax.swing.JTextField field1,
        String label2, javax.swing.JTextField field2,
        String label3, javax.swing.JTextField field3) {
        JPanel wrapper = new JPanel(new GridLayout(0, 1));
        wrapper.add(profileRow(label1, field1));
        JPanel pair = new JPanel(new GridLayout(1, 2, 6, 0));
        pair.add(profileRow(label2, field2));
        pair.add(profileRow(label3, field3));
        wrapper.add(pair);
        return wrapper;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 1 ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
