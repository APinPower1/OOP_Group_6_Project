package edu.mec.studentportal.testsupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TestDatabaseUrlValidatorTest {
    @Test
    void acceptsDefaultExampleUrl() {
        assertDoesNotThrow(() -> TestDatabaseUrlValidator.validate(
            TestDatabaseUrlValidator.REQUIRED_SCHEMA_EXAMPLE_URL));
    }

    @Test
    void acceptsUrlWithServerTimezoneAndOtherParams() {
        assertDoesNotThrow(() -> TestDatabaseUrlValidator.validate(
            "jdbc:mysql://localhost:3306/student_portal_test?useSSL=false&serverTimezone=Asia/Kolkata"));
    }

    @Test
    void acceptsUrlWithNonDefaultPort() {
        assertDoesNotThrow(() -> TestDatabaseUrlValidator.validate(
            "jdbc:mysql://db.local:3307/student_portal_test"));
    }

    @Test
    void rejectsProductionSchema() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://localhost:3306/student_portal"));
        assertRedacted(ex);
    }

    @Test
    void rejectsOtherSchema() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://localhost:3306/other_db"));
        assertRedacted(ex);
    }

    @Test
    void rejectsBlankUrl() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("   "));
        assertRedacted(ex);
    }

    @Test
    void rejectsMissingSchema() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://localhost:3306/"));
        assertRedacted(ex);
    }

    @Test
    void rejectsNestedSchemaPath() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://localhost:3306/student_portal_test/extra"));
        assertRedacted(ex);
    }

    @Test
    void rejectsEmbeddedUserInfo() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://user:pass@localhost:3306/student_portal_test"));
        assertRedacted(ex);
    }

    @Test
    void rejectsWrongProtocol() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:postgresql://localhost:5432/student_portal_test"));
        assertRedacted(ex);
    }

    @Test
    void rejectsMissingHost() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql:///student_portal_test"));
        assertRedacted(ex);
    }

    @Test
    void rejectsInvalidPort() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://localhost:70000/student_portal_test"));
        assertRedacted(ex);
    }

    @Test
    void rejectsNonNumericPort() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://localhost:notaport/student_portal_test"));
        assertRedacted(ex);
    }

    @Test
    void rejectsMalformedUri() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://localhost:3306/[bad"));
        assertRedacted(ex);
    }

    @Test
    void errorMessageContainsSafeExampleAndRequiredSchema() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> TestDatabaseUrlValidator.validate("jdbc:mysql://localhost:3306/student_portal"));
        String msg = ex.getMessage();
        org.junit.jupiter.api.Assertions.assertTrue(
            msg.startsWith("Unsafe or misconfigured TEST_DB_URL:"));
        org.junit.jupiter.api.Assertions.assertTrue(
            msg.contains(TestDatabaseUrlValidator.REQUIRED_SCHEMA));
        org.junit.jupiter.api.Assertions.assertTrue(
            msg.contains(TestDatabaseUrlValidator.REQUIRED_SCHEMA_EXAMPLE_URL));
    }

    private static void assertRedacted(IllegalStateException ex) {
        String msg = ex.getMessage();
        org.junit.jupiter.api.Assertions.assertNotNull(msg);
        org.junit.jupiter.api.Assertions.assertFalse(msg.isBlank());
        org.junit.jupiter.api.Assertions.assertFalse(msg.contains("user:pass"));
    }
}
