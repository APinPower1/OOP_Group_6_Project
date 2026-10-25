package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.ui.theme.PortalTheme;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;

/**
 * Time-of-day picker: fixed 15-minute steps from 00:00 to 23:45. A server value that
 * falls between steps snaps down to the nearest step, so nothing is ever unselectable.
 */
public final class TimeField extends JComboBox<String> {
    static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ISO_LOCAL_TIME;

    public TimeField() {
        super(buildSteps().toArray(new String[0]));
        setEditable(false);
        setFont(PortalTheme.SM);
        setSelectedItem("09:00");
    }

    private static List<String> buildSteps() {
        List<String> steps = new ArrayList<>(96);
        for (int hour = 0; hour < 24; hour++) {
            for (int minute = 0; minute < 60; minute += 15) {
                steps.add(String.format("%02d:%02d", hour, minute));
            }
        }
        return steps;
    }

    /** Selected time, or null when nothing is selected. */
    public LocalTime getTime() {
        Object item = getSelectedItem();
        if (item == null) {
            return null;
        }
        try {
            return LocalTime.parse(item.toString(), ISO_TIME);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    public void setTime(LocalTime time) {
        if (time == null) {
            setSelectedItem(null);
            return;
        }
        String exact = time.format(ISO_TIME);
        String stepped = String.format("%02d:%02d", time.getHour(),
            (time.getMinute() / 15) * 15);
        setSelectedItem(hasItem(exact) ? exact : stepped);
    }

    private boolean hasItem(String value) {
        for (int index = 0; index < getItemCount(); index++) {
            if (value.equals(getItemAt(index))) {
                return true;
            }
        }
        return false;
    }

    public void setText(String text) {
        if (text == null || text.isBlank()) {
            setSelectedItem(null);
            return;
        }
        try {
            setTime(LocalTime.parse(text.trim(), ISO_TIME));
        } catch (DateTimeParseException exception) {
            setSelectedItem(null);
        }
    }
}
