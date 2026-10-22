package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.UserSummary;
import javax.swing.JPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Role navigation and dashboard construction are headless-safe (no JFrame/dialogs): each role
 * maps to exactly one dashboard type. Network refresh is intentionally not triggered here.
 */
class RoleNavigationTest {
    private SessionContext session;

    /** ApiClient stub: dashboards must not call it during construction. */
    private static final ApiClient NO_API = new ApiClient() {
        @Override
        public <T> T get(String path, Class<T> responseType) {
            throw new AssertionError("no API call expected during construction: " + path);
        }

        @Override
        public <T> T get(String path, TypeReference<T> responseType) {
            throw new AssertionError("no API call expected during construction: " + path);
        }

        @Override
        public <T> T post(String path, Object requestBody, Class<T> responseType) {
            throw new AssertionError("no API call expected during construction: " + path);
        }

        @Override
        public <T> T post(String path, Object requestBody, TypeReference<T> responseType) {
            throw new AssertionError("no API call expected during construction: " + path);
        }

        @Override
        public <T> T put(String path, Object requestBody, Class<T> responseType) {
            throw new AssertionError("no API call expected during construction: " + path);
        }

        @Override
        public <T> T patch(String path, Object requestBody, Class<T> responseType) {
            throw new AssertionError("no API call expected during construction: " + path);
        }

        @Override
        public void delete(String path) {
            throw new AssertionError("no API call expected during construction: " + path);
        }

        @Override
        public <T> T delete(String path, Class<T> responseType) {
            throw new AssertionError("no API call expected during construction: " + path);
        }
    };

    @BeforeEach
    void setUp() {
        session = new SessionContext();
    }

    private void loginAs(Role role) {
        session.setSession("token", new UserSummary(1, "One User", "one@mec.edu", role));
    }

    @Test
    void dashboardTypeMapsEveryRole() {
        assertSame(StudentDashboard.class, RoleNavigation.dashboardType(Role.STUDENT));
        assertSame(OrganizerDashboard.class, RoleNavigation.dashboardType(Role.ORGANIZER));
        assertSame(FacultyDashboard.class, RoleNavigation.dashboardType(Role.FACULTY));
        assertSame(AdminDashboard.class, RoleNavigation.dashboardType(Role.ADMIN));
    }

    @Test
    void createDashboardBuildsTheRoleDashboard() {
        for (Role role : Role.values()) {
            loginAs(role);
            JPanel panel = RoleNavigation.createDashboard(role, NO_API, session);
            assertInstanceOf(RoleNavigation.dashboardType(role), panel,
                "role " + role + " must get its dashboard");
            assertTrue(panel instanceof DashboardPanel);
        }
    }

    @Test
    void dashboardsCarryTheLoggedInIdentity() {
        loginAs(Role.ORGANIZER);
        JPanel panel = RoleNavigation.createDashboard(Role.ORGANIZER, NO_API, session);
        assertInstanceOf(OrganizerDashboard.class, panel);
    }
}
