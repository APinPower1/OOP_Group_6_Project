package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Picker fields must round-trip ISO values and reject garbage without throwing. */
class DateTimeFieldTest {
    @Test
    void dateFieldRoundTripsIsoDate() {
        DateField field = new DateField();

        field.setDate(LocalDate.of(2026, 3, 10));
        assertEquals("2026-03-10", field.getText());
        assertEquals(LocalDate.of(2026, 3, 10), field.getDate());
    }

    @Test
    void dateFieldRejectsGarbageAndEmptyInput() {
        DateField field = new DateField();

        field.setText("not-a-date");
        assertNull(field.getDate());

        field.setText("");
        assertNull(field.getDate());

        field.setText(null);
        assertNull(field.getDate());
    }

    @Test
    void timeFieldDefaultsToNineAndSnapsToQuarterSteps() {
        TimeField field = new TimeField();
        assertEquals(LocalTime.of(9, 0), field.getTime());

        field.setTime(LocalTime.of(10, 7));
        assertEquals(LocalTime.of(10, 0), field.getTime());

        field.setTime(LocalTime.of(23, 45));
        assertEquals(LocalTime.of(23, 45), field.getTime());
    }

    @Test
    void timeFieldParsesIsoTextAndClearsOnGarbage() {
        TimeField field = new TimeField();

        field.setText("14:30");
        assertEquals(LocalTime.of(14, 30), field.getTime());

        field.setText("quarter past");
        assertNull(field.getTime());

        field.setTime(null);
        assertNull(field.getTime());
    }
}
