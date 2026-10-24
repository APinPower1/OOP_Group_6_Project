package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.ui.theme.Glyph;
import edu.mec.studentportal.client.ui.theme.PortalTheme;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.BorderLayout;
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
import javax.swing.border.EmptyBorder;

/**
 * Date entry field: {@code yyyy-MM-dd} text input plus a hand-built month-grid popup
 * (no third-party date library — shared build forbids new dependencies).
 */
public final class DateField extends JPanel {
    static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JTextField field = new JTextField(10);

    public DateField() {
        super(new BorderLayout(5, 0));
        setOpaque(false);
        UiKit.enableTextEditing(field);
        field.setFont(PortalTheme.SM);
        field.setToolTipText("yyyy-MM-dd — type it or pick from the calendar");
        add(field, BorderLayout.CENTER);

        JButton pick = UiKit.ghostButton("Pick", Glyph.CALENDAR);
        pick.setPreferredSize(new Dimension(74, 30));
        pick.addActionListener(event -> showCalendar());
        add(pick, BorderLayout.EAST);
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
        popup.add(buildCalendar(popup, month, current));
        popup.show(this, 0, getHeight());
    }

    private JPanel buildCalendar(JPopupMenu popup, YearMonth month, LocalDate selected) {
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(PortalTheme.CARD);

        JLabel title = new JLabel(month.getMonth() + " " + month.getYear(),
            JLabel.CENTER);
        title.setFont(PortalTheme.SM_BOLD);
        title.setForeground(PortalTheme.INK);

        JButton prev = smallNav("‹");
        JButton next = smallNav("›");
        prev.addActionListener(event -> replace(popup,
            buildCalendar(popup, month.minusMonths(1), selected)));
        next.addActionListener(event -> replace(popup,
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
            grid.add(dayButton(popup, date, selected, todayMonth));
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

    private JButton dayButton(JPopupMenu popup, LocalDate date, LocalDate selected,
        YearMonth todayMonth) {
        JButton button = new JButton(Integer.toString(date.getDayOfMonth()));
        button.setFont(PortalTheme.SM);
        button.setPreferredSize(new Dimension(34, 26));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        boolean isSelected = selected != null && date.equals(selected);
        boolean isToday = date.getYear() == todayMonth.getYear()
            && date.getMonth() == todayMonth.getMonth()
            && date.getDayOfMonth() == LocalDate.now().getDayOfMonth();
        if (isSelected) {
            button.setBackground(PortalTheme.ACCENT);
            button.setForeground(java.awt.Color.WHITE);
        } else {
            button.setOpaque(true);
            button.setBackground(PortalTheme.CARD);
            button.setForeground(isToday ? PortalTheme.ACCENT : PortalTheme.INK);
            if (isToday) {
                button.setFont(PortalTheme.SM_BOLD);
            }
        }
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (!isSelected) {
                    button.setBackground(PortalTheme.ACCENT_SOFT);
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (!isSelected) {
                    button.setBackground(PortalTheme.CARD);
                }
            }
        });
        button.addActionListener(event -> {
            setDate(date);
            popup.setVisible(false);
        });
        return button;
    }

    private static JButton smallNav(String text) {
        JButton button = new JButton(text);
        button.setFont(PortalTheme.SM_BOLD);
        button.setPreferredSize(new Dimension(30, 26));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(PortalTheme.SLATE_SOFT);
        button.setForeground(PortalTheme.INK);
        return button;
    }

    private void replace(JPopupMenu popup, JPanel content) {
        popup.removeAll();
        popup.add(content);
        popup.revalidate();
        popup.repaint();
    }
}
