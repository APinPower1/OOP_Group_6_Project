package edu.mec.studentportal.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserTest {
    private static final String PASSWORD_HASH = "$2a$10$demoHashedPassword";

    @Test
    void studentReportsStudentRole() {
        assertEquals(Role.STUDENT, student(true).getRole());
    }

    @Test
    void organizerReportsOrganizerRole() {
        assertEquals(Role.ORGANIZER, organizer(true).getRole());
    }

    @Test
    void facultyReportsFacultyRole() {
        assertEquals(Role.FACULTY, faculty(true).getRole());
    }

    @Test
    void adminReportsAdminRole() {
        assertEquals(Role.ADMIN, admin(true).getRole());
    }

    @Test
    void commonFieldsAreReadable() {
        Student student = student(true);

        assertEquals(41L, student.getId());
        assertEquals("Demo Student", student.getFullName());
        assertEquals("student@example.edu", student.getEmail());
        assertEquals("Computer Science", student.getDepartment());
        assertEquals(PASSWORD_HASH, student.passwordHash());
        assertEquals("R001", student.getProfile().getRollNumber());
        assertEquals(3, student.getProfile().getSemester());
        assertEquals("2024", student.getProfile().getBatch());
        assertTrue(student.isEnabled());
    }

    @Test
    void disabledStateIsReadable() {
        assertFalse(student(false).isEnabled());
    }

    @Test
    void passwordHashIsNotExposedAsJavaBeanGetter() {
        assertThrows(
            NoSuchMethodException.class,
            () -> User.class.getDeclaredMethod("getPasswordHash")
        );
    }

    @Test
    void studentProfileRejectsSemesterBelowOne() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new StudentProfile("R001", 0, "2024")
        );
    }

    @Test
    void adminProfileRejectsAdminLevelBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new AdminProfile(0));
    }

    @Test
    void adminProfileDefaultsToLevelOne() {
        assertEquals(1, new AdminProfile().getAdminLevel());
        assertEquals(2, new AdminProfile(2).getAdminLevel());
    }

    @Test
    void profilesRejectNullRequiredValues() {
        assertThrows(IllegalArgumentException.class, () -> new StudentProfile(null, 1, "2024"));
        assertThrows(IllegalArgumentException.class, () -> new StudentProfile("R001", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new OrganizerProfile(null));
        assertThrows(IllegalArgumentException.class, () -> new FacultyProfile(null, "Professor"));
        assertThrows(IllegalArgumentException.class, () -> new FacultyProfile("E001", null));
    }

    @Test
    void userRejectsNullCommonFields() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Student(1L, null, "s@example.edu", PASSWORD_HASH, "CS", true, studentProfile())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new Student(1L, "Demo", null, PASSWORD_HASH, "CS", true, studentProfile())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new Student(1L, "Demo", "s@example.edu", null, "CS", true, studentProfile())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new Student(1L, "Demo", "s@example.edu", PASSWORD_HASH, null, true, studentProfile())
        );
    }

    @Test
    void concreteUsersRequireProfile() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Student(1L, "Demo", "s@example.edu", PASSWORD_HASH, "CS", true, null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new Organizer(2L, "Demo", "o@example.edu", PASSWORD_HASH, "CS", true, null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new Faculty(3L, "Demo", "f@example.edu", PASSWORD_HASH, "CS", true, null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new Admin(4L, "Demo", "a@example.edu", PASSWORD_HASH, "CS", true, null)
        );
    }

    private static StudentProfile studentProfile() {
        return new StudentProfile("R001", 3, "2024");
    }

    private static Student student(boolean enabled) {
        return new Student(
            41L,
            "Demo Student",
            "student@example.edu",
            PASSWORD_HASH,
            "Computer Science",
            enabled,
            studentProfile()
        );
    }

    private static Organizer organizer(boolean enabled) {
        return new Organizer(
            42L,
            "Demo Organizer",
            "organizer@example.edu",
            PASSWORD_HASH,
            "Computer Science",
            enabled,
            new OrganizerProfile("Club Coordinator")
        );
    }

    private static Faculty faculty(boolean enabled) {
        return new Faculty(
            43L,
            "Demo Faculty",
            "faculty@example.edu",
            PASSWORD_HASH,
            "Computer Science",
            enabled,
            new FacultyProfile("E001", "Professor")
        );
    }

    private static Admin admin(boolean enabled) {
        return new Admin(
            44L,
            "Demo Admin",
            "admin@example.edu",
            PASSWORD_HASH,
            "Computer Science",
            enabled,
            new AdminProfile()
        );
    }
}
