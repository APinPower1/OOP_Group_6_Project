package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.event.FocusEvent;
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

    @Test
    void dateFieldBlocksPickingUntilTheGuardAllowsIt() {
        DateField field = new DateField();
        int[] notified = {0};
        field.setSelectionBlocked(() -> true, () -> notified[0]++);

        field.pickButton().doClick();

        assertEquals(1, notified[0]);
        assertNull(field.getDate());
    }

    @Test
    void focusOnBlockedFieldsNotifiesAndLiftsWhenGuardAllows() {
        DateField dateField = new DateField();
        TimeField timeField = new TimeField();
        boolean[] startMissing = {true};
        int[] notified = {0};
        Runnable warn = () -> notified[0]++;
        dateField.setSelectionBlocked(() -> startMissing[0], warn);
        timeField.setSelectionBlocked(() -> startMissing[0], warn);

        fireFocusGain(dateField.inputField(), DateField.class);
        fireFocusGain(timeField, TimeField.class);
        assertEquals(2, notified[0]);

        startMissing[0] = false;
        fireFocusGain(dateField.inputField(), DateField.class);
        fireFocusGain(timeField, TimeField.class);
        assertEquals(2, notified[0]);
    }

    /** Swing components ignore synthetic focus dispatch, so invoke the registered listener. */
    private static void fireFocusGain(java.awt.Component component, Class<?> owner) {
        for (java.awt.event.FocusListener listener : component.getFocusListeners()) {
            if (listener.getClass().getEnclosingClass() == owner) {
                listener.focusGained(new FocusEvent(component, FocusEvent.FOCUS_GAINED));
            }
        }
    }

    @Test
    void pickValidatorRejectsEndBeforeStart() {
        DateField field = new DateField();
        assertNull(field.pickValidator().apply(LocalDate.of(2026, 6, 1)));

        field.setPickValidator(date -> date.isBefore(LocalDate.of(2026, 6, 10))
            ? "End date must be after the start date." : null);

        assertEquals("End date must be after the start date.",
            field.pickValidator().apply(LocalDate.of(2026, 6, 1)));
        assertNull(field.pickValidator().apply(LocalDate.of(2026, 6, 15)));
    }
}
