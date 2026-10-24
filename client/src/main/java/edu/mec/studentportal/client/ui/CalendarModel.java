package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.dto.EventDto;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure month-grid logic for the event calendar: navigation, Monday-first week layout and
 * bucketing of events by their start date. No Swing and no API calls — fully unit-testable.
 * Events outside the displayed month are kept in the bucket map so switching months is free.
 */
public final class CalendarModel {
    public static final DayOfWeek WEEK_START = DayOfWeek.MONDAY;

    /** One rendered day cell: the date, whether it belongs to the displayed month, its events. */
    public record DayCell(LocalDate date, boolean inMonth, List<EventDto> events) {
        public DayCell {
            events = List.copyOf(events);
        }
    }

    private final LocalDate today;
    private YearMonth displayed;
    private Map<LocalDate, List<EventDto>> eventsByDay = Map.of();

    public CalendarModel(LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today must not be null");
        }
        this.today = today;
        this.displayed = YearMonth.from(today);
    }

    public LocalDate today() {
        return today;
    }

    public YearMonth displayedMonth() {
        return displayed;
    }

    public void previousMonth() {
        displayed = displayed.minusMonths(1);
    }

    public void nextMonth() {
        displayed = displayed.plusMonths(1);
    }

    public void goTo(YearMonth month) {
        if (month == null) {
            throw new IllegalArgumentException("month must not be null");
        }
        displayed = month;
    }

    public void goToday() {
        displayed = YearMonth.from(today);
    }

    /** Replaces the event index; events are bucketed by {@code startTime}'s date. */
    public void setEvents(List<EventDto> events) {
        if (events == null || events.isEmpty()) {
            eventsByDay = Map.of();
            return;
        }
        Map<LocalDate, List<EventDto>> bucket = new HashMap<>();
        for (EventDto event : events) {
            if (event == null || event.startTime() == null) {
                continue;
            }
            bucket.computeIfAbsent(event.startTime().toLocalDate(), day -> new ArrayList<>())
                .add(event);
        }
        Map<LocalDate, List<EventDto>> frozen = new HashMap<>();
        bucket.forEach((day, list) -> {
            list.sort((left, right) -> left.startTime().compareTo(right.startTime()));
            frozen.put(day, Collections.unmodifiableList(list));
        });
        eventsByDay = Collections.unmodifiableMap(frozen);
    }

    public int eventCount() {
        int total = 0;
        for (List<EventDto> day : eventsByDay.values()) {
            total += day.size();
        }
        return total;
    }

    public List<EventDto> eventsOn(LocalDate day) {
        return eventsByDay.getOrDefault(day, List.of());
    }

    /**
     * Monday-first grid rows covering every day of the displayed month (5 or 6 full weeks).
     * Leading/trailing cells belong to the adjacent months and are flagged {@code inMonth=false}.
     */
    public List<List<DayCell>> grid() {
        YearMonth month = displayed;
        int firstOffset = month.atDay(1).getDayOfWeek().getValue() - WEEK_START.getValue();
        if (firstOffset < 0) {
            firstOffset += 7;
        }
        int daysInMonth = month.lengthOfMonth();
        int totalCells = firstOffset + daysInMonth;
        int weeks = (totalCells + 6) / 7;
        LocalDate cursor = month.atDay(1).minusDays(firstOffset);
        List<List<DayCell>> rows = new ArrayList<>(weeks);
        for (int week = 0; week < weeks; week++) {
            List<DayCell> row = new ArrayList<>(7);
            for (int day = 0; day < 7; day++) {
                row.add(new DayCell(cursor, cursor.getMonth() == month.getMonth(),
                    eventsOn(cursor)));
                cursor = cursor.plusDays(1);
            }
            rows.add(Collections.unmodifiableList(row));
        }
        return Collections.unmodifiableList(rows);
    }

    /** ISO day labels for the weekday header row, e.g. "Mon" … "Sun". */
    public static List<String> weekdayLabels() {
        List<String> labels = new ArrayList<>(7);
        DayOfWeek cursor = WEEK_START;
        for (int i = 0; i < 7; i++) {
            labels.add(cursor.toString().substring(0, 1)
                + cursor.toString().substring(1).toLowerCase());
            cursor = cursor.plus(1);
        }
        return labels;
    }

    /** Stable key list used by tests to assert month layout without exposing internals. */
    public Map<LocalDate, List<EventDto>> snapshot() {
        return new LinkedHashMap<>(eventsByDay);
    }
}
