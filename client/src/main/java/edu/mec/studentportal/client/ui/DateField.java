package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.ui.theme.Glyph;
import edu.mec.studentportal.client.ui.theme.PortalTheme;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Date entry field: {@code yyyy-MM-dd} text input plus a hand-built month-grid popup
 * (no third-party date library — shared build forbids new dependencies). The grid cells
 * are plain labels: the platform button UI renders the day numbers unreliably at this
 * size, while labels render consistently everywhere.
 */
public final class DateField extends JPanel {
    static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Dimension POPUP_SIZE = new Dimension(300, 280);

    private final JTextField field = new JTextField(10);
    private final JButton pick;
    private java.util.function.BooleanSupplier blocked = () -> false;
    private Runnable onBlocked = () -> { };
    private java.util.function.Function<LocalDate, String> pickValidator = date -> null;

    public DateField() {
        super(new BorderLayout(5, 0));
        setOpaque(false);
        UiKit.enableTextEditing(field);
        field.setFont(PortalTheme.SM);
        field.setToolTipText("yyyy-MM-dd — type it or pick from the calendar");
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                if (blocked.getAsBoolean()) {
                    onBlocked.run();
                }
            }
        });
        add(field, BorderLayout.CENTER);

        pick = UiKit.ghostButton("Pick", Glyph.CALENDAR);
        pick.setPreferredSize(new Dimension(74, 30));
        pick.addActionListener(event -> {
            if (blocked.getAsBoolean()) {
                onBlocked.run();
                return;
            }
            showCalendar();
        });
        add(pick, BorderLayout.EAST);
    }

    /**
     * Blocks date selection (typing or picking) while the predicate holds; the runnable
     * is shown instead — used by the event form so the start must be chosen first.
     */
    public void setSelectionBlocked(java.util.function.BooleanSupplier blocked,
        Runnable onBlocked) {
        this.blocked = blocked == null ? () -> false : blocked;
        this.onBlocked = onBlocked == null ? () -> { } : onBlocked;
    }

    /** Rejects a picked date with an error message (returned) before it is committed. */
    public void setPickValidator(java.util.function.Function<LocalDate, String> validator) {
        this.pickValidator = validator == null ? date -> null : validator;
    }

    java.util.function.Function<LocalDate, String> pickValidator() {
        return pickValidator;
    }

    /** Moves keyboard focus into the text input (used to send the user back to the start). */
    public void requestInputFocus() {
        field.requestFocusInWindow();
    }

    JTextField inputField() {
        return field;
    }

    JButton pickButton() {
        return pick;
    }

    /** Parsed date, or null when the field is empty or not {@code yyyy-MM-dd}. */
    public LocalDate getDate() {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text, ISO_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    public void setDate(LocalDate date) {
        field.setText(date == null ? "" : date.format(ISO_DATE));
    }

    public void setText(String text) {
        field.setText(text == null ? "" : text);
    }

    public String getText() {
        return field.getText().trim();
    }

    private void showCalendar() {
        LocalDate current = getDate();
        YearMonth month = current == null ? YearMonth.now() : YearMonth.from(current);
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPanel cal = buildCalendar(popup, month, current);
        cal.setPreferredSize(POPUP_SIZE);
        popup.add(cal);
        popup.pack();
        popup.show(this, 0, getHeight());
    }

    private JPanel buildCalendar(JPopupMenu popup, YearMonth month, LocalDate selected) {
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(PortalTheme.CARD);

        JLabel title = new JLabel(month.getMonth() + " " + month.getYear(),
            JLabel.CENTER);
        title.setFont(PortalTheme.SM_BOLD);
        title.setForeground(PortalTheme.INK);

        JLabel prev = navCell("‹");
        JLabel next = navCell("›");
        interactable(prev, null, () -> replace(popup,
            buildCalendar(popup, month.minusMonths(1), selected)));
        interactable(next, null, () -> replace(popup,
            buildCalendar(popup, month.plusMonths(1), selected)));

        JPanel head = new JPanel(new BorderLayout(6, 0));
        head.setOpaque(false);
        head.add(prev, BorderLayout.WEST);
        head.add(title, BorderLayout.CENTER);
        head.add(next, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        grid.setOpaque(false);
        for (String weekday : new String[] {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"}) {
            JLabel label = new JLabel(weekday, JLabel.CENTER);
            label.setFont(PortalTheme.XS);
            label.setForeground(PortalTheme.MUTED);
            grid.add(label);
        }
        YearMonth todayMonth = YearMonth.now();
        LocalDate first = month.atDay(1);
        int leading = first.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < leading; i++) {
            grid.add(new JLabel(" "));
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            grid.add(dayCell(popup, date, selected, todayMonth));
        }
        root.add(grid, BorderLayout.CENTER);

        JButton today = UiKit.ghostButton("Today");
        today.addActionListener(event -> {
            setDate(LocalDate.now());
            popup.setVisible(false);
        });
        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.add(today, BorderLayout.WEST);
        root.add(foot, BorderLayout.SOUTH);
        return root;
    }

    private JLabel dayCell(JPopupMenu popup, LocalDate date, LocalDate selected,
        YearMonth todayMonth) {
        boolean isSelected = selected != null && date.equals(selected);
        boolean isToday = date.getYear() == todayMonth.getYear()
            && date.getMonth() == todayMonth.getMonth()
            && date.getDayOfMonth() == LocalDate.now().getDayOfMonth();
        JLabel cell = flatCell(Integer.toString(date.getDayOfMonth()),
            isSelected ? PortalTheme.ACCENT : PortalTheme.CARD,
            isSelected ? Color.WHITE : isToday ? PortalTheme.ACCENT : PortalTheme.INK,
            isToday ? PortalTheme.SM_BOLD : PortalTheme.SM);
        if (isSelected) {
            cell.putClientProperty("selected", Boolean.TRUE);
        }
        interactable(cell, PortalTheme.ACCENT_SOFT, () -> {
            String problem = pickValidator.apply(date);
            if (problem != null) {
                UiSupport.info(this, problem);
                if (!popup.isVisible()) {
                    // the modal message dismisses the calendar — bring it straight back
                    popup.show(this, 0, getHeight());
                }
                return;
            }
            setDate(date);
            popup.setVisible(false);
        });
        return cell;
    }

    private static JLabel navCell(String glyph) {
        return flatCell(glyph, PortalTheme.SLATE_SOFT, PortalTheme.INK,
            PortalTheme.SM_BOLD);
    }

    private static JLabel flatCell(String text, Color background, Color foreground,
        java.awt.Font font) {
        JLabel cell = new JLabel(text, SwingConstants.CENTER);
        cell.setFont(font);
        cell.setOpaque(true);
        cell.setBackground(background);
        cell.setForeground(foreground);
        cell.setVerticalAlignment(SwingConstants.CENTER);
        cell.setBorder(new EmptyBorder(4, 2, 4, 2));
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return cell;
    }

    /** Click action plus (optional) hover tint; selected cells keep their colour. */
    private static void interactable(JLabel cell, Color hover, Runnable onClick) {
        Color base = cell.getBackground();
        cell.putClientProperty("baseBg", base);
        cell.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (hover != null && cell.getClientProperty("selected") == null) {
                    cell.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (cell.getClientProperty("selected") == null) {
                    cell.setBackground(base);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                onClick.run();
            }
        });
    }

    private void replace(JPopupMenu popup, JPanel content) {
        popup.removeAll();
        content.setPreferredSize(POPUP_SIZE);
        popup.add(content);
        popup.pack();
        popup.revalidate();
        popup.repaint();
    }
}
