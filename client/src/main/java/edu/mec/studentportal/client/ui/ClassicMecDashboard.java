package edu.mec.studentportal.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.AnnouncementDto;
import edu.mec.studentportal.client.dto.ClubDto;
import edu.mec.studentportal.client.dto.EventDto;
import edu.mec.studentportal.client.dto.NotificationDto;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

/** 
 * Clean overview/front page - no large heading box, maximum use of space.
 * Shows consolidated view of announcements, events, clubs, and notifications at a glance.
 */
public final class ClassicMecDashboard extends JPanel implements MainFrame.Refreshable {
    private static final Color BG_LIGHT = new Color(0xF8F9FA);
    private static final Color SIDEBAR_DARK = new Color(0x2C2C2C);
    private static final Color ACCENT_MAROON = new Color(0xA03033);
    private static final Color CARD_BORDER = new Color(0xE0E0E0);
    private static final Color TEXT_DARK = new Color(0x1A1A1A);
    private static final Color TEXT_MUTED = new Color(0x666666);
    private static final Color CARD_GRAY = new Color(0xF5F5F5);
    private static final Color SUCCESS_GREEN = new Color(0x2E7D32);
    
    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 10);
    
    private static final TypeReference<List<AnnouncementDto>> ANNOUNCEMENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<EventDto>> EVENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ClubDto>> CLUB_LIST = new TypeReference<>() {};
    private static final TypeReference<List<NotificationDto>> NOTIFICATION_LIST = new TypeReference<>() {};
    
    private final transient ApiClient api;
    private final transient SessionContext session;
    
    private final JPanel overviewPanel = new JPanel();
    private final JLabel statusLabel = new JLabel("Loading overview...");
    
    public ClassicMecDashboard(ApiClient api, SessionContext session) {
        super(new BorderLayout());
        this.api = api;
        this.session = session;
        setBackground(BG_LIGHT);
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMainContent(), BorderLayout.CENTER);
    }
    
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(SIDEBAR_DARK);
        sidebar.setPreferredSize(new Dimension(60, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        
        sidebar.add(createNavIcon("⌂"));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(createNavIcon("📅"));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(createNavIcon("👥"));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(createNavIcon("📢"));
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(createNavIcon("🔔"));
        
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(createNavIcon("⎋"));
        
        return sidebar;
    }
    
    private JLabel createNavIcon(String symbol) {
        JLabel icon = new JLabel(symbol, SwingConstants.CENTER);
        icon.setForeground(Color.WHITE);
        icon.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        icon.setPreferredSize(new Dimension(60, 40));
        icon.setMaximumSize(new Dimension(60, 40));
        icon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        icon.setOpaque(true);
        icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                icon.setBackground(new Color(60, 60, 60));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                icon.setBackground(SIDEBAR_DARK);
            }
        });
        return icon;
    }
    
    private JPanel buildMainContent() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG_LIGHT);
        main.setBorder(BorderFactory.createEmptyBorder(12, 18, 10, 18));
        
        // Top bar - minimal, just user info and date
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_LIGHT);
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        
        String userName = session.getUser() != null ? session.getUser().fullName() : "Student";
        JLabel userLabel = new JLabel(userName + " • " + (session.getRole() != null ? session.getRole().name() : "STUDENT"));
        userLabel.setFont(FONT_BODY);
        userLabel.setForeground(TEXT_DARK);
        
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
        JLabel dateLabel = new JLabel(date);
        dateLabel.setFont(FONT_SMALL);
        dateLabel.setForeground(TEXT_MUTED);
        
        topBar.add(userLabel, BorderLayout.WEST);
        topBar.add(dateLabel, BorderLayout.EAST);
        
        main.add(topBar, BorderLayout.NORTH);
        
        // Overview grid
        overviewPanel.setLayout(new GridLayout(2, 3, 12, 12));
        overviewPanel.setBackground(BG_LIGHT);
        
        JScrollPane scrollPane = new JScrollPane(overviewPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(BG_LIGHT);
        scrollPane.getViewport().setBackground(BG_LIGHT);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        
        main.add(scrollPane, BorderLayout.CENTER);
        
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        statusBar.setBackground(BG_LIGHT);
        statusBar.add(statusLabel);
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(TEXT_MUTED);
        main.add(statusBar, BorderLayout.SOUTH);
        
        return main;
    }
    
    private RoundedPanel createSectionCard(String title, Color accentColor) {
        RoundedPanel card = new RoundedPanel(10);
        card.setBackground(Color.WHITE);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FONT_HEADER);
        titleLabel.setForeground(TEXT_DARK);
        
        JPanel accentBar = new JPanel();
        accentBar.setBackground(accentColor);
        accentBar.setPreferredSize(new Dimension(3, 16));
        
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleRow.setBackground(Color.WHITE);
        titleRow.add(accentBar);
        titleRow.add(titleLabel);
        
        header.add(titleRow, BorderLayout.WEST);
        
        card.add(header, BorderLayout.NORTH);
        
        return card;
    }
    
    @Override
    public void refreshAll() {
        statusLabel.setText("Refreshing...");
        overviewPanel.removeAll();
        
        // Announcements section
        RoundedPanel annCard = createSectionCard("ANNOUNCEMENTS", ACCENT_MAROON);
        JPanel annContent = new JPanel();
        annContent.setLayout(new BoxLayout(annContent, BoxLayout.Y_AXIS));
        annContent.setBackground(Color.WHITE);
        annCard.add(annContent, BorderLayout.CENTER);
        overviewPanel.add(annCard);
        
        ApiTask.run(
            () -> api.get("/announcements", ANNOUNCEMENT_LIST),
            list -> {
                annContent.removeAll();
                List<AnnouncementDto> anns = list == null ? List.of() : list;
                if (anns.isEmpty()) {
                    JLabel empty = new JLabel("No new announcements");
                    empty.setFont(FONT_BODY);
                    empty.setForeground(TEXT_MUTED);
                    annContent.add(empty);
                } else {
                    int count = Math.min(4, anns.size());
                    for (int i = 0; i < count; i++) {
                        AnnouncementDto ann = anns.get(i);
                        JLabel item = new JLabel("<html><b>" + truncate(ann.title(), 60) + "</b><br><font color='#666666'>" 
                            + (ann.createdAt() != null ? ann.createdAt().format(DateTimeFormatter.ofPattern("dd MMM")) : "") + "</font></html>");
                        item.setFont(FONT_BODY);
                        item.setForeground(TEXT_DARK);
                        item.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
                        annContent.add(item);
                    }
                }
                annContent.revalidate();
                annContent.repaint();
            },
            err -> {}
        );
        
        // Events section
        RoundedPanel eventCard = createSectionCard("UPCOMING EVENTS", SUCCESS_GREEN);
        JPanel eventContent = new JPanel();
        eventContent.setLayout(new BoxLayout(eventContent, BoxLayout.Y_AXIS));
        eventContent.setBackground(Color.WHITE);
        eventCard.add(eventContent, BorderLayout.CENTER);
        overviewPanel.add(eventCard);
        
        ApiTask.run(
            () -> api.get("/events", EVENT_LIST),
            list -> {
                eventContent.removeAll();
                List<EventDto> events = list == null ? List.of() : list;
                if (events.isEmpty()) {
                    JLabel empty = new JLabel("No upcoming events");
                    empty.setFont(FONT_BODY);
                    empty.setForeground(TEXT_MUTED);
                    eventContent.add(empty);
                } else {
                    int count = Math.min(4, events.size());
                    for (int i = 0; i < count; i++) {
                        EventDto ev = events.get(i);
                        String timeStr = ev.startTime() != null ? ev.startTime().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")) : "";
                        JLabel item = new JLabel("<html><b>" + truncate(ev.title(), 55) + "</b><br><font color='#666666'>" 
                            + timeStr + " • " + ev.venue() + "</font></html>");
                        item.setFont(FONT_BODY);
                        item.setForeground(TEXT_DARK);
                        item.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
                        eventContent.add(item);
                    }
                }
                eventContent.revalidate();
                eventContent.repaint();
            },
            err -> {}
        );
        
        // Clubs section
        RoundedPanel clubCard = createSectionCard("CLUBS", new Color(0x1565C0));
        JPanel clubContent = new JPanel();
        clubContent.setLayout(new BoxLayout(clubContent, BoxLayout.Y_AXIS));
        clubContent.setBackground(Color.WHITE);
        clubCard.add(clubContent, BorderLayout.CENTER);
        overviewPanel.add(clubCard);
        
        ApiTask.run(
            () -> api.get("/clubs", CLUB_LIST),
            list -> {
                clubContent.removeAll();
                List<ClubDto> clubs = list == null ? List.of() : list;
                if (clubs.isEmpty()) {
                    JLabel empty = new JLabel("No clubs available");
                    empty.setFont(FONT_BODY);
                    empty.setForeground(TEXT_MUTED);
                    clubContent.add(empty);
                } else {
                    int count = Math.min(4, clubs.size());
                    for (int i = 0; i < count; i++) {
                        ClubDto club = clubs.get(i);
                        JLabel item = new JLabel("<html><b>" + truncate(club.name(), 55) + "</b><br><font color='#666666'>" 
                            + club.category() + " • " + club.activeMemberCount() + " members</font></html>");
                        item.setFont(FONT_BODY);
                        item.setForeground(TEXT_DARK);
                        item.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
                        clubContent.add(item);
                    }
                }
                clubContent.revalidate();
                clubContent.repaint();
            },
            err -> {}
        );
        
        // Notifications section
        RoundedPanel notifCard = createSectionCard("NOTIFICATIONS", new Color(0xF57C00));
        JPanel notifContent = new JPanel();
        notifContent.setLayout(new BoxLayout(notifContent, BoxLayout.Y_AXIS));
        notifContent.setBackground(Color.WHITE);
        notifCard.add(notifContent, BorderLayout.CENTER);
        overviewPanel.add(notifCard);
        
        ApiTask.run(
            () -> api.get("/notifications/me", NOTIFICATION_LIST),
            list -> {
                notifContent.removeAll();
                List<NotificationDto> notifs = list == null ? List.of() : list;
                if (notifs.isEmpty()) {
                    JLabel empty = new JLabel("No notifications");
                    empty.setFont(FONT_BODY);
                    empty.setForeground(TEXT_MUTED);
                    notifContent.add(empty);
                } else {
                    int count = Math.min(4, notifs.size());
                    for (int i = 0; i < count; i++) {
                        NotificationDto n = notifs.get(i);
                        String readStatus = n.readAt() == null ? "• " : "";
                        JLabel item = new JLabel("<html>" + readStatus + "<b>" + truncate(n.title(), 50) + "</b><br><font color='#666666'>" 
                            + (n.createdAt() != null ? n.createdAt().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")) : "") + "</font></html>");
                        item.setFont(FONT_BODY);
                        item.setForeground(TEXT_DARK);
                        item.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
                        notifContent.add(item);
                    }
                }
                notifContent.revalidate();
                notifContent.repaint();
                statusLabel.setText("Last updated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            },
            err -> statusLabel.setText("Some data failed to load")
        );
        
        // Quick Actions
        RoundedPanel actionsCard = createSectionCard("QUICK ACTIONS", new Color(0x6A1B9A));
        JPanel actionsContent = new JPanel();
        actionsContent.setLayout(new BoxLayout(actionsContent, BoxLayout.Y_AXIS));
        actionsContent.setBackground(Color.WHITE);
        
        JButton regBtn = new JButton("Browse & Register for Events");
        regBtn.setFont(FONT_BODY);
        regBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        regBtn.setForeground(ACCENT_MAROON);
        regBtn.setBorderPainted(false);
        regBtn.setContentAreaFilled(false);
        regBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        regBtn.setHorizontalAlignment(SwingConstants.LEFT);
        
        JButton joinBtn = new JButton("Join/Leave Clubs");
        joinBtn.setFont(FONT_BODY);
        joinBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinBtn.setForeground(ACCENT_MAROON);
        joinBtn.setBorderPainted(false);
        joinBtn.setContentAreaFilled(false);
        joinBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        joinBtn.setHorizontalAlignment(SwingConstants.LEFT);
        
        JButton feedbackBtn = new JButton("Submit Feedback");
        feedbackBtn.setFont(FONT_BODY);
        feedbackBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        feedbackBtn.setForeground(ACCENT_MAROON);
        feedbackBtn.setBorderPainted(false);
        feedbackBtn.setContentAreaFilled(false);
        feedbackBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        feedbackBtn.setHorizontalAlignment(SwingConstants.LEFT);
        
        actionsContent.add(regBtn);
        actionsContent.add(joinBtn);
        actionsContent.add(feedbackBtn);
        actionsCard.add(actionsContent, BorderLayout.CENTER);
        overviewPanel.add(actionsCard);
        
        // Summary
        RoundedPanel summaryCard = createSectionCard("OVERVIEW", TEXT_MUTED);
        JPanel summaryContent = new JPanel();
        summaryContent.setLayout(new BoxLayout(summaryContent, BoxLayout.Y_AXIS));
        summaryContent.setBackground(Color.WHITE);
        
        JLabel sum1 = new JLabel("• Stay updated with announcements");
        sum1.setFont(FONT_BODY);
        sum1.setForeground(TEXT_DARK);
        JLabel sum2 = new JLabel("• Register for upcoming events");
        sum2.setFont(FONT_BODY);
        sum2.setForeground(TEXT_DARK);
        JLabel sum3 = new JLabel("• Join clubs and connect");
        sum3.setFont(FONT_BODY);
        sum3.setForeground(TEXT_DARK);
        
        summaryContent.add(sum1);
        summaryContent.add(sum2);
        summaryContent.add(sum3);
        summaryCard.add(summaryContent, BorderLayout.CENTER);
        overviewPanel.add(summaryCard);
        
        overviewPanel.revalidate();
        overviewPanel.repaint();
    }
    
    private String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
    
    /** Custom rounded panel for cards */
    static class RoundedPanel extends JPanel {
        private final int radius;
        
        RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius, radius));
            g2.setColor(CARD_BORDER);
            g2.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
