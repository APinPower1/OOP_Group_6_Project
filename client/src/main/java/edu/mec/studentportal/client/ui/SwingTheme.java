package edu.mec.studentportal.client.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/** Shared visual defaults for the desktop client. */
public final class SwingTheme {
    public static final Color NAVY = new Color(42, 42, 83);
    public static final Color ACCENT = new Color(253, 45, 52);
    public static final Color GOLD = new Color(255, 200, 112);
    public static final Color PAGE = new Color(248, 249, 250);
    public static final Color SURFACE = Color.WHITE;
    public static final Color TEXT = new Color(51, 51, 51);
    public static final Color MUTED = new Color(103, 105, 125);
    public static final Color BORDER = new Color(224, 224, 232);

    private SwingTheme() {}

    public static void install() {
        FontUIResource body = new FontUIResource("SansSerif", Font.PLAIN, 13);
        FontUIResource strong = new FontUIResource("SansSerif", Font.BOLD, 13);

        UIManager.put("Panel.background", PAGE);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", body);
        UIManager.put("Button.font", strong);
        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.foreground", NAVY);
        UIManager.put("Button.focus", new Color(180, 197, 228));
        UIManager.put("Button.margin", new java.awt.Insets(8, 14, 8, 14));
        UIManager.put("ButtonUI", InteractiveButtonUI.class.getName());
        UIManager.put("TextField.border", javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(BORDER),
            javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        UIManager.put("TextField.font", body);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("PasswordField.border", javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(BORDER),
            javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        UIManager.put("PasswordField.font", body);
        UIManager.put("PasswordField.background", SURFACE);
        UIManager.put("PasswordField.foreground", TEXT);
        UIManager.put("PasswordField.caretForeground", ACCENT);
        UIManager.put("ComboBox.font", body);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("Spinner.font", body);
        UIManager.put("TabbedPane.font", strong);
        UIManager.put("TabbedPane.background", PAGE);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.tabInsets", new java.awt.Insets(10, 14, 10, 14));
        UIManager.put("TabbedPane.contentBorderInsets", new java.awt.Insets(12, 10, 10, 10));
        UIManager.put("Table.font", body);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.selectionBackground", new Color(238, 237, 249));
        UIManager.put("Table.selectionForeground", NAVY);
        UIManager.put("Table.gridColor", new Color(237, 237, 243));
        UIManager.put("Table.rowHeight", 34);
        UIManager.put("Table.showGrid", false);
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
        UIManager.put("TableHeader.font", new FontUIResource("SansSerif", Font.BOLD, 12));
        UIManager.put("TableHeader.background", new Color(239, 243, 247));
        UIManager.put("TableHeader.foreground", NAVY);
        UIManager.put("TableHeader.reorderingAllowed", false);
        UIManager.put("ScrollPane.background", SURFACE);
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createLineBorder(BORDER));
        UIManager.put("OptionPane.messageFont", body);
        UIManager.put("OptionPane.buttonFont", strong);
    }
}
