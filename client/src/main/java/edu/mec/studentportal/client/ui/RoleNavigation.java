package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.Role;
import javax.swing.JPanel;

/**
 * Role-based navigation map: which dashboard each role gets after login. Role-based hiding is
 * a UX convenience only — every API call is re-authorized by the backend.
 */
public final class RoleNavigation {
    private RoleNavigation() {}

    public static JPanel createDashboard(Role role, ApiClient api, SessionContext session) {
        return switch (role) {
            case STUDENT -> new StudentDashboard(api, session);
            case ORGANIZER -> new OrganizerDashboard(api, session);
            case FACULTY -> new FacultyDashboard(api, session);
            case ADMIN -> new AdminDashboard(api, session);
        };
    }

    static Class<? extends JPanel> dashboardType(Role role) {
        return switch (role) {
            case STUDENT -> StudentDashboard.class;
            case ORGANIZER -> OrganizerDashboard.class;
            case FACULTY -> FacultyDashboard.class;
            case ADMIN -> AdminDashboard.class;
        };
    }
}
