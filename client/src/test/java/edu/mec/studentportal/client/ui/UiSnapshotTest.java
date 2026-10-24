package edu.mec.studentportal.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.EventStatus;
import edu.mec.studentportal.client.dto.Role;
import edu.mec.studentportal.client.dto.UserSummary;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * Headless render check: paints the login screen, a role dashboard and the event calendar
 * offscreen into PNGs so the layout can be inspected without a display. Also guards against
 * accidental API calls during construction (the stub throws on any use).
 */
class UiSnapshotTest {
    private static final ApiClient NO_API = new ApiClient() {
        @Override
        public <T> T get(String path, Class<T> responseType) {
            throw new AssertionError("no API call expected: " + path);
        }

        @Override
        public <T> T get(String path, TypeReference<T> responseType) {
            throw new AssertionError("no API call expected: " + path);
        }

        @Override
        public <T> T post(String path, Object requestBody, Class<T> responseType) {
            throw new AssertionError("no API call expected: " + path);
        }

        @Override
        public <T> T post(String path, Object requestBody, TypeReference<T> responseType) {
            throw new AssertionError("no API call expected: " + path);
        }

        @Override
        public <T> T put(String path, Object requestBody, Class<T> responseType) {
            throw new AssertionError("no API call expected: " + path);
        }

        @Override
        public <T> T patch(String path, Object requestBody, Class<T> responseType) {
            throw new AssertionError("no API call expected: " + path);
        }

        @Override
        public void delete(String path) {
            throw new AssertionError("no API call expected: " + path);
        }

        @Override
        public <T> T delete(String path, Class<T> responseType) {
            throw new AssertionError("no API call expected: " + path);
        }
    };

    @Test
    void rendersLoginStudentDashboardAndCalendar() throws Exception {
        UiKit.install();
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "portal-ui-snapshots");
        Files.createDirectories(dir);

        SessionContext anon = new SessionContext();
        LoginView login = new LoginView(NO_API, anon);
        writePng(login, 1280, 820, dir.resolve("login.png"));

        SessionContext student = new SessionContext();
        student.setSession("token", new UserSummary(1, "Asha Menon", "asha@mec.edu", Role.STUDENT, false));
        JPanel dashboard = RoleNavigation.createDashboard(Role.STUDENT, NO_API, student);
        writePng(dashboard, 1280, 820, dir.resolve("student-dashboard.png"));
        if (dashboard instanceof DashboardPanel panel && panel.tabs.getSelectedIndex() == 0) {
            panel.tabs.setSelectedIndex(1);
            writePng(dashboard, 1280, 820, dir.resolve("student-events-tab.png"));
        }

        SessionContext organizer = new SessionContext();
        organizer.setSession("token",
            new UserSummary(2, "Rahul Nair", "rahul@mec.edu", Role.ORGANIZER, false));
        JPanel organizerDashboard = RoleNavigation.createDashboard(Role.ORGANIZER, NO_API,
            organizer);
        writePng(organizerDashboard, 1280, 820, dir.resolve("organizer-dashboard.png"));

        EventCalendarPanel calendar = new EventCalendarPanel(NO_API, anon);
        calendar.setEvents(sampleEvents());
        writePng(calendar, 1100, 720, dir.resolve("calendar.png"));
    }

    private static List<EventDto> sampleEvents() {
        LocalDateTime today = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0)
            .withNano(0);
        return List.of(
            event(1, "Tech Club Weekly Meetup", today, EventStatus.APPROVED),
            event(2, "Robotics Workshop", today.withHour(14), EventStatus.APPROVED),
            event(3, "Cultural Night Planning", today.plusDays(1).withHour(11),
                EventStatus.PENDING_APPROVAL),
            event(4, "Alumni Talk: Career Paths", today.plusDays(3).withHour(16),
                EventStatus.APPROVED),
            event(5, "Sports Day Rehearsal", today.plusDays(5).withHour(9),
                EventStatus.CANCELLED),
            event(6, "Open Mic Evening", today.plusDays(5).withHour(18),
                EventStatus.APPROVED)
        );
    }

    private static EventDto event(long id, String title, LocalDateTime start, EventStatus status) {
        return new EventDto(id, title, "Sample description", "Main Auditorium",
            start, start.plusHours(2), 80, status, 3L, null, null, start, start, 24, 56);
    }

    private static void writePng(JComponent component, int width, int height, Path file)
        throws Exception {
        component.setSize(width, height);
        layoutDeep(component);
        component.addNotify();
        layoutDeep(component);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paintAll(graphics);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private static void layoutDeep(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layoutDeep(nested);
            }
        }
    }
}
