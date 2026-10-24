package edu.mec.studentportal.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.EventStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarModelTest {
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 3, 15);

    private static EventDto event(long id, String title, LocalDateTime start) {
        return new EventDto(id, title, "", "Hall", start, start.plusHours(2),
            50, EventStatus.APPROVED, 1L, null, null, start, start, 10, 40);
    }

    @Test
    void weekdayLabelsAreMondayFirst() {
        List<String> labels = CalendarModel.weekdayLabels();
        assertEquals(7, labels.size());
        assertEquals("Monday", labels.get(0));
        assertEquals("Sunday", labels.get(6));
    }

    @Test
    void displayedMonthDefaultsToToday() {
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        assertEquals(YearMonth.of(2026, 3), model.displayedMonth());
        assertEquals(FIXED_TODAY, model.today());
    }

    @Test
    void navigationMovesMonth() {
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        model.nextMonth();
        assertEquals(YearMonth.of(2026, 4), model.displayedMonth());
        model.previousMonth();
        model.previousMonth();
        assertEquals(YearMonth.of(2026, 2), model.displayedMonth());
        model.goToday();
        assertEquals(YearMonth.of(2026, 3), model.displayedMonth());
    }

    @Test
    void goToAcceptsArbitraryMonth() {
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        model.goTo(YearMonth.of(2025, 12));
        assertEquals(YearMonth.of(2025, 12), model.displayedMonth());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CalendarModel(null));
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        assertThrows(IllegalArgumentException.class, () -> model.goTo(null));
    }

    @Test
    void gridCoversWholeMonthMondayFirst() {
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        List<List<CalendarModel.DayCell>> grid = model.grid();
        assertTrue(grid.size() >= 5 && grid.size() <= 6);
        for (List<CalendarModel.DayCell> row : grid) {
            assertEquals(7, row.size());
        }
        LocalDate first = grid.get(0).get(0).date();
        assertEquals(CalendarModel.WEEK_START, first.getDayOfWeek());
        int inMonth = 0;
        for (List<CalendarModel.DayCell> row : grid) {
            for (CalendarModel.DayCell cell : row) {
                if (cell.inMonth()) {
                    inMonth++;
                }
            }
        }
        assertEquals(31, inMonth, "March 2026 has 31 days");
    }

    @Test
    void eventsBucketByStartDateSortedByTime() {
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        LocalDateTime day = FIXED_TODAY.atStartOfDay();
        model.setEvents(List.of(
            event(1, "Later", day.withHour(15)),
            event(2, "Earlier", day.withHour(9)),
            event(3, "Other day", day.plusDays(2).withHour(11))
        ));
        assertEquals(3, model.eventCount());
        List<EventDto> today = model.eventsOn(FIXED_TODAY);
        assertEquals(2, today.size());
        assertEquals("Earlier", today.get(0).title());
        assertEquals("Later", today.get(1).title());
        assertEquals(1, model.eventsOn(FIXED_TODAY.plusDays(2)).size());
    }

    @Test
    void setEventsHandlesEmptyAndNull() {
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        model.setEvents(null);
        assertEquals(0, model.eventCount());
        model.setEvents(List.of());
        assertEquals(0, model.eventCount());
        model.setEvents(java.util.Arrays.asList((EventDto) null,
            event(1, "Only", FIXED_TODAY.atStartOfDay())));
        assertEquals(1, model.eventCount());
    }

    @Test
    void eventsOnUnknownDayReturnsEmpty() {
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        assertTrue(model.eventsOn(FIXED_TODAY).isEmpty());
    }

    @Test
    void snapshotExposesBucketKeys() {
        CalendarModel model = new CalendarModel(FIXED_TODAY);
        model.setEvents(List.of(event(1, "A", FIXED_TODAY.atStartOfDay())));
        assertTrue(model.snapshot().containsKey(FIXED_TODAY));
        assertFalse(model.snapshot().containsKey(FIXED_TODAY.plusDays(1)));
    }
}
