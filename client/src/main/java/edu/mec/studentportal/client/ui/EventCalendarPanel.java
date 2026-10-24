package edu.mec.studentportal.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.EventStatus;
import edu.mec.studentportal.client.ui.theme.Glyph;
import edu.mec.studentportal.client.ui.theme.PortalTheme;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Month calendar of campus events, available to every role right after login. The left grid
 * shows day cells with event chips (status-coloured); the right rail lists the selected day's
 * events in detail. Data comes from {@code GET /events}, so the backend's role rules decide
 * exactly what each user may see (students: approved; organizers: their own, etc.).
 */
public final class EventCalendarPanel extends JPanel {
    private static final TypeReference<List<EventDto>> EVENT_LIST = new TypeReference<>() {};
    private static final DateTimeFormatter MONTH_TITLE =
        DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter DAY_TITLE =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final transient ApiClient api;
    private final CalendarModel model;
    private final MonthGrid grid = new MonthGrid();
    private final DayDetail detail = new DayDetail();
    private final JLabel monthTitle = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton refreshButton = circleButton(Glyph.REFRESH, "Refresh events");
    private javax.swing.JSplitPane splitPane;
    private boolean autoRefreshed;

    public EventCalendarPanel(ApiClient api, SessionContext session) {
        super(new BorderLayout(0, 14));
        this.api = api;
        this.model = new CalendarModel(LocalDate.now());
        setBackground(PortalTheme.CANVAS);
        setBorder(new EmptyBorder(22, 24, 20, 24));

        add(buildToolbar(), BorderLayout.NORTH);

        grid.setModel(model);
        grid.setOnSelect(date -> {
            detail.show(model, date);
            repaint();
        });

        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setBorder(null);
        detailScroll.getViewport().setBackground(Color.WHITE);
        detailScroll.setPreferredSize(new Dimension(340, 0));
        detailScroll.setMinimumSize(new Dimension(280, 0));

        splitPane = new javax.swing.JSplitPane(
            javax.swing.JSplitPane.HORIZONTAL_SPLIT, grid, detailScroll);
        splitPane.setResizeWeight(1.0);
        splitPane.setBorder(null);
        splitPane.setDividerSize(10);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerLocation(700);
        // the day-plan rail keeps a readable width; the month grid absorbs every
        // other pixel, so maximizing the window grows the calendar instead of
        // leaving a giant empty rail
        splitPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                applySplitProportions();
            }
        });
        add(splitPane, BorderLayout.CENTER);

        refreshButton.addActionListener(event -> refresh());
        showMonth();
        detail.show(model, model.today());
    }

    /** Triggers the first data load once the panel enters a realized window. */
    @Override
    public void addNotify() {
        super.addNotify();
        applySplitProportions();
        if (!autoRefreshed) {
            autoRefreshed = true;
            javax.swing.SwingUtilities.invokeLater(this::refresh);
        }
    }

    /** Rail width scales with the window: 340px small, up to 560px on big screens. */
    private void applySplitProportions() {
        if (splitPane == null) {
            return;
        }
        int total = splitPane.getWidth();
        if (total <= 0) {
            return;
        }
        int rail = Math.max(340, Math.min(560, Math.round(total * 0.26f)));
        int left = total - rail - splitPane.getDividerSize();
        if (left >= 420 && Math.abs(splitPane.getDividerLocation() - left) > 2) {
            splitPane.setDividerLocation(left);
        }
    }

    /** Programmatic data injection — used by the snapshot tool (and handy for tests). */
    void setEvents(List<EventDto> events) {
        model.setEvents(events);
        grid.repaint();
        detail.show(model, grid.getSelected());
    }

    /** Reloads events from the API; called on first show, by the refresh button. */
    void refresh() {
        statusLabel.setForeground(PortalTheme.MUTED);
        statusLabel.setText("Loading events…");
        refreshButton.setEnabled(false);
        ApiTask.run(
            () -> api.get("/events", EVENT_LIST),
            loaded -> {
                refreshButton.setEnabled(true);
                model.setEvents(loaded == null ? List.of() : loaded);
                statusLabel.setForeground(PortalTheme.MUTED);
                statusLabel.setText(loaded == null || loaded.isEmpty()
                    ? "No events yet" : loaded.size() + " events loaded");
                showMonth();
                grid.repaint();
                detail.show(model, grid.getSelected());
            },
            error -> {
                refreshButton.setEnabled(true);
                statusLabel.setForeground(PortalTheme.DANGER);
                statusLabel.setText("Could not load events — tap refresh to retry.");
            },
            refreshButton
        );
    }

    private void showMonth() {
        monthTitle.setText(model.displayedMonth().format(MONTH_TITLE));
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        monthTitle.setFont(PortalTheme.XL_BOLD);
        monthTitle.setForeground(PortalTheme.INK);
        JButton today = UiKit.pillButton("Today");
        today.addActionListener(event -> {
            model.goToday();
            showMonth();
            grid.repaint();
            detail.show(model, model.today());
        });
        left.add(monthTitle);
        left.add(today);
        toolbar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        for (EventStatus status : EventStatus.values()) {
            right.add(UiKit.chip(labelFor(status),
                PortalTheme.statusForeground(status), PortalTheme.statusBackground(status)));
        }
        right.add(Box.createHorizontalStrut(10));
        right.add(statusLabel);
        right.add(refreshButton);
        right.add(circleButton(Glyph.CHEVRON_LEFT, "Previous month", event -> {
            model.previousMonth();
            showMonth();
            grid.repaint();
        }));
        right.add(circleButton(Glyph.CHEVRON_RIGHT, "Next month", event -> {
            model.nextMonth();
            showMonth();
            grid.repaint();
        }));
        toolbar.add(right, BorderLayout.EAST);
        return toolbar;
    }

    private static String labelFor(EventStatus status) {
        return status.name().replace('_', ' ');
    }

    private static JButton circleButton(Glyph glyph, String tooltip) {
        return circleButton(glyph, tooltip, null);
    }

    private static JButton circleButton(Glyph glyph, String tooltip,
        java.awt.event.ActionListener action) {
        CircleIconButton button = new CircleIconButton(glyph);
        button.setToolTipText(tooltip);
        if (action != null) {
            button.addActionListener(action);
        }
        return button;
    }

    // ------------------------------------------------------------------ month grid

    private final class MonthGrid extends JComponent {
        private CalendarModel calendar;
        private LocalDate selected;
        private LocalDate hovered;
        private java.util.function.Consumer<LocalDate> onSelect = date -> { };

        private MonthGrid() {
            setOpaque(true);
            setBackground(Color.WHITE);
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    LocalDate date = hit(event.getX(), event.getY());
                    if (date != null) {
                        selected = date;
                        detail.show(calendar, date);
                        onSelect.accept(date);
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent event) {
                    LocalDate date = hit(event.getX(), event.getY());
                    if (!java.util.Objects.equals(date, hovered)) {
                        hovered = date;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = null;
                    repaint();
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        private void setModel(CalendarModel calendar) {
            this.calendar = calendar;
            this.selected = calendar.today();
        }

        private void setOnSelect(java.util.function.Consumer<LocalDate> consumer) {
            onSelect = consumer;
        }

        private LocalDate getSelected() {
            return selected;
        }

        private int headerHeight() {
            return 34;
        }

        private LocalDate hit(int x, int y) {
            if (calendar == null || getHeight() <= headerHeight()) {
                return null;
            }
            List<List<CalendarModel.DayCell>> weeks = calendar.grid();
            int columns = 7;
            int cellW = Math.max(1, getWidth() / columns);
            int cellH = Math.max(1, (getHeight() - headerHeight()) / weeks.size());
            int col = x / cellW;
            int row = (y - headerHeight()) / cellH;
            if (y < headerHeight() || col < 0 || col > 6 || row < 0 || row >= weeks.size()) {
                return null;
            }
            return weeks.get(row).get(col).date();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int width = getWidth();
                int header = headerHeight();
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, width, getHeight());

                if (calendar == null) {
                    return;
                }
                g2.setFont(PortalTheme.SM_BOLD);
                FontMetrics metrics = g2.getFontMetrics();
                List<String> weekdays = CalendarModel.weekdayLabels();
                int cellW = width / 7;
                for (int col = 0; col < 7; col++) {
                    g2.setColor(PortalTheme.MUTED);
                    String label = weekdays.get(col).toUpperCase();
                    g2.drawString(label, col * cellW + (cellW - metrics.stringWidth(label)) / 2f,
                        (header - metrics.getHeight()) / 2f + metrics.getAscent());
                }
                g2.setColor(PortalTheme.HAIRLINE);
                g2.fillRect(0, header - 1, width, 1);

                List<List<CalendarModel.DayCell>> weeks = calendar.grid();
                int cellH = Math.max(1, (getHeight() - header) / weeks.size());
                for (int row = 0; row < weeks.size(); row++) {
                    for (int col = 0; col < 7; col++) {
                        CalendarModel.DayCell cell = weeks.get(row).get(col);
                        paintCell(g2, cell, col * cellW, header + row * cellH,
                            cellW, cellH);
                    }
                }
                g2.setColor(PortalTheme.HAIRLINE);
                for (int col = 1; col < 7; col++) {
                    g2.drawLine(col * cellW, header, col * cellW, getHeight());
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintCell(Graphics2D g2, CalendarModel.DayCell cell,
            int x, int y, int w, int h) {
            LocalDate date = cell.date();
            boolean isToday = date.equals(calendar.today());
            boolean isSelected = date.equals(selected);
            boolean isHovered = date.equals(hovered);
            int pad = 4;

            if (isSelected) {
                g2.setPaint(new java.awt.GradientPaint(x, y, PortalTheme.ACCENT,
                    x + w, y + h, PortalTheme.ACCENT_2));
                g2.fillRoundRect(x + pad, y + pad, w - pad * 2, h - pad * 2, 12, 12);
            } else if (isToday) {
                UiKit.roundFill(g2, x + pad, y + pad, w - pad * 2, h - pad * 2, 12,
                    PortalTheme.ACCENT_SOFT);
                UiKit.roundStroke(g2, x + pad + 0.5, y + pad + 0.5, w - pad * 2 - 1,
                    h - pad * 2 - 1, 12, PortalTheme.ACCENT, 1.5f);
            } else if (!cell.inMonth()) {
                UiKit.roundFill(g2, x + pad, y + pad, w - pad * 2, h - pad * 2, 12,
                    new Color(0xF6, 0xF6, 0xF8));
            } else if (isHovered) {
                UiKit.roundFill(g2, x + pad, y + pad, w - pad * 2, h - pad * 2, 12,
                    new Color(0xFD, 0xF0, 0xF1));
            }

            Color dayColor = isSelected ? Color.WHITE
                : cell.inMonth() ? PortalTheme.INK : new Color(0xA6, 0xAA, 0xB2);
            g2.setColor(dayColor);
            g2.setFont((isToday || isSelected) ? PortalTheme.MD_BOLD : PortalTheme.SM);
            FontMetrics metrics = g2.getFontMetrics();
            String dayLabel = String.valueOf(date.getDayOfMonth());
            g2.drawString(dayLabel, x + 12, y + 12 + metrics.getAscent());

            if (!cell.events().isEmpty()) {
                int chipY = y + 12 + metrics.getAscent() + 7;
                int visible = h > 108 ? 3 : h > 78 ? 2 : 1;
                List<EventDto> events = cell.events();
                for (int i = 0; i < Math.min(visible, events.size()); i++) {
                    EventDto event = events.get(i);
                    chipY = paintChip(g2, event, x + 8, chipY, w - 16, isSelected);
                }
                if (events.size() > visible) {
                    g2.setFont(PortalTheme.XS);
                    g2.setColor(isSelected ? new Color(0xFF, 0xE2, 0xE3) : PortalTheme.MUTED);
                    g2.drawString("+" + (events.size() - visible) + " more",
                        x + 12, chipY + 9);
                }
            }
        }

        private int paintChip(Graphics2D g2, EventDto event, int x, int y, int width,
            boolean onAccent) {
            g2.setFont(PortalTheme.XS);
            FontMetrics metrics = g2.getFontMetrics();
            int chipH = 17;
            Color fill = onAccent ? new Color(0xFF, 0xFF, 0xFF, 56)
                : PortalTheme.statusBackground(event.status());
            UiKit.roundFill(g2, x, y, width, chipH, 8, fill);
            Color dot = onAccent ? Color.WHITE : PortalTheme.statusForeground(event.status());
            g2.setColor(dot);
            g2.fillOval(x + 6, y + (chipH - 6) / 2, 6, 6);
            g2.setColor(onAccent ? Color.WHITE : PortalTheme.INK);
            String label = UiKit.ellipsize(metrics, event.title(), width - 24);
            g2.drawString(label, x + 17, y + (metrics.getAscent() + (chipH - metrics.getAscent() - metrics.getDescent()) / 2));
            return y + chipH + 4;
        }
    }

    // ------------------------------------------------------------------ day detail

    private final class DayDetail extends JPanel {
        private final JLabel title = new JLabel();
        private final JLabel countChip = UiKit.chip("0 EVENTS", PortalTheme.ACCENT,
            PortalTheme.ACCENT_SOFT);
        private final JPanel list = new JPanel();
        private final JLabel emptyLabel = new JLabel("No events scheduled", SwingConstants.CENTER);
        private LocalDate shownDate;

        private DayDetail() {
            super(new BorderLayout(0, 14));
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(24, 22, 18, 22));

            JPanel header = new JPanel(new BorderLayout(0, 8));
            header.setOpaque(false);
            title.setFont(PortalTheme.LG_BOLD);
            title.setForeground(PortalTheme.INK);
            JPanel titleRow = new JPanel(new BorderLayout());
            titleRow.setOpaque(false);
            titleRow.add(title, BorderLayout.WEST);
            titleRow.add(countChip, BorderLayout.EAST);
            header.add(titleRow, BorderLayout.NORTH);
            JLabel subtitle = new JLabel("Day plan");
            subtitle.setFont(PortalTheme.EYEBROW);
            subtitle.setForeground(PortalTheme.ACCENT);
            header.add(subtitle, BorderLayout.SOUTH);
            add(header, BorderLayout.NORTH);

            list.setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            emptyLabel.setFont(PortalTheme.SM);
            emptyLabel.setForeground(PortalTheme.MUTED);
            JPanel emptyWrap = new JPanel(new BorderLayout(0, 10));
            emptyWrap.setOpaque(false);
            emptyWrap.setBorder(new EmptyBorder(40, 10, 10, 10));
            JComponent bigGlyph = new JComponent() {
                @Override
                protected void paintComponent(Graphics graphics) {
                    Glyph.CALENDAR.paint((Graphics2D) graphics, 0, 0, 44,
                        new Color(0xD9, 0xA2, 0xA5));
                }
            };
            bigGlyph.setPreferredSize(new Dimension(44, 44));
            bigGlyph.setAlignmentX(Component.CENTER_ALIGNMENT);
            JPanel glyphRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            glyphRow.setOpaque(false);
            glyphRow.add(bigGlyph);
            emptyWrap.add(glyphRow, BorderLayout.NORTH);
            emptyWrap.add(emptyLabel, BorderLayout.CENTER);

            JScrollPane scroll = new JScrollPane(list);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(Color.WHITE);
            add(scroll, BorderLayout.CENTER);
            add(emptyWrap, BorderLayout.SOUTH);
        }

        private void show(CalendarModel calendar, LocalDate date) {
            if (calendar == null || date == null) {
                return;
            }
            shownDate = date;
            List<EventDto> events = calendar.eventsOn(date);
            title.setText(date.format(DAY_TITLE));
            countChip.setText(events.size() == 1 ? "1 EVENT" : events.size() + " EVENTS");
            list.removeAll();
            if (events.isEmpty()) {
                emptyLabel.setText("No events on " + date.format(DAY_TITLE));
                scroll(list).setVisible(false);
            } else {
                for (EventDto event : events) {
                    list.add(new EventCard(event));
                    list.add(Box.createVerticalStrut(10));
                }
                scroll(list).setVisible(true);
            }
            list.revalidate();
            list.repaint();
            revalidate();
            repaint();
        }

        private JScrollPane scroll(JPanel target) {
            for (java.awt.Component child : getComponents()) {
                if (child instanceof JScrollPane pane && pane.getViewport().getView() == target) {
                    return pane;
                }
            }
            return new JScrollPane();
        }

        private final class EventCard extends JPanel {
            private EventCard(EventDto event) {
                setOpaque(true);
                setBackground(new Color(0xFA, 0xF8, 0xF8));
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                setAlignmentX(Component.LEFT_ALIGNMENT);
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
                setBorder(new EmptyBorder(14, 14, 14, 14));

                JPanel timeRow = new JPanel(new BorderLayout(8, 0));
                timeRow.setOpaque(false);
                timeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel time = new JLabel(event.startTime().toLocalTime().format(TIME)
                    + " – " + event.endTime().toLocalTime().format(TIME));
                time.setFont(PortalTheme.SM_BOLD);
                time.setForeground(PortalTheme.ACCENT);
                timeRow.add(time, BorderLayout.WEST);
                timeRow.add(UiKit.chip(event.status().name().replace('_', ' '),
                    PortalTheme.statusForeground(event.status()),
                    PortalTheme.statusBackground(event.status())), BorderLayout.EAST);
                add(timeRow);

                JLabel name = new JLabel("<html><b>" + escape(event.title()) + "</b></html>");
                name.setFont(PortalTheme.MD_BOLD);
                name.setForeground(PortalTheme.INK);
                name.setAlignmentX(Component.LEFT_ALIGNMENT);
                add(Box.createVerticalStrut(5));
                add(name);

                JLabel venue = new JLabel(event.venue() == null ? "" : event.venue());
                venue.setFont(PortalTheme.SM);
                venue.setForeground(PortalTheme.MUTED);
                venue.setIcon(null);
                venue.setAlignmentX(Component.LEFT_ALIGNMENT);
                add(Box.createVerticalStrut(4));
                add(venue);

                if (event.registeredCount() != null && event.capacity() > 0) {
                    JLabel seats = new JLabel(event.registeredCount() + " / "
                        + event.capacity() + " seats");
                    seats.setFont(PortalTheme.XS);
                    seats.setForeground(PortalTheme.MUTED);
                    seats.setAlignmentX(Component.LEFT_ALIGNMENT);
                    add(Box.createVerticalStrut(4));
                    add(seats);
                }
            }

            private String escape(String text) {
                return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;");
            }
        }
    }

    // ------------------------------------------------------------------ icon button

    private static final class CircleIconButton extends UiKit.AnimatedButton {
        private final Glyph glyph;

        private CircleIconButton(Glyph glyph) {
            super(glyph == Glyph.CHEVRON_LEFT ? "Previous month" : "Next month");
            this.glyph = glyph;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setPreferredSize(new Dimension(38, 38));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                float hover = hoverAmount();
                g2.translate(0, pressAmount() * 0.7);
                Color bg = UiKit.blend(Color.WHITE, PortalTheme.ACCENT_SOFT,
                    Math.max(hover, pressAmount()));
                g2.setColor(bg);
                g2.fillOval(1, 2, w - 2, h - 4);
                g2.setColor(UiKit.blend(PortalTheme.BORDER, PortalTheme.ACCENT, hover));
                g2.setStroke(new java.awt.BasicStroke(1.3f));
                g2.draw(new java.awt.geom.Ellipse2D.Double(1.5, 2.5, w - 3, h - 5));
                if (!isEnabled()) {
                    glyph.paint(g2, (w - 17) / 2.0, (h - 19) / 2.0, 17,
                        new Color(0xA6, 0xAA, 0xB2));
                } else {
                    glyph.paint(g2, (w - 17) / 2.0, (h - 19) / 2.0, 17,
                        UiKit.blend(PortalTheme.INK, PortalTheme.ACCENT, hover));
                }
                paintFocusRing(g2, w, h, h / 2.0);
            } finally {
                g2.dispose();
            }
        }
    }
}
