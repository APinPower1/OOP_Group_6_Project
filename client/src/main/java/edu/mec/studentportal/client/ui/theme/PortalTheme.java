package edu.mec.studentportal.client.ui.theme;

import edu.mec.studentportal.client.dto.EventStatus;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * Design tokens for the refreshed UI — a clear, neutral workspace with MEC red
 * accent. Fonts resolve to the best installed modern UI face (SF Pro
 * on macOS, Segoe UI on Windows) with a safe logical fallback everywhere else.
 */
public final class PortalTheme {
    public static final Color SIDEBAR_TOP = Color.WHITE;
    public static final Color SIDEBAR_BOTTOM = Color.WHITE;
    public static final Color SIDEBAR_TEXT = new Color(0x4B, 0x55, 0x66);
    public static final Color SIDEBAR_TEXT_DIM = new Color(0x7C, 0x86, 0x98);
    public static final Color SIDEBAR_HOVER = new Color(0xFD, 0xEF, 0xF0);

    public static final Color CANVAS = new Color(0xF7, 0xF7, 0xF8);
    public static final Color CARD = Color.WHITE;
    public static final Color INK = new Color(0x1D, 0x27, 0x3A);
    public static final Color MUTED = new Color(0x6C, 0x76, 0x88);
    public static final Color BORDER = new Color(0xE0, 0xE6, 0xEF);
    public static final Color HAIRLINE = new Color(0xEC, 0xF0, 0xF5);

    public static final Color ACCENT = new Color(0xD8, 0x21, 0x28);
    public static final Color ACCENT_2 = new Color(0xEE, 0x4B, 0x52);
    public static final Color ACCENT_SOFT = new Color(0xFC, 0xE8, 0xE9);
    public static final Color CYAN = ACCENT;

    public static final Color SUCCESS = new Color(0x15, 0x80, 0x3D);
    public static final Color SUCCESS_SOFT = new Color(0xE9, 0xF6, 0xEE);
    public static final Color WARN = new Color(0xB4, 0x53, 0x09);
    public static final Color WARN_SOFT = new Color(0xFD, 0xF2, 0xE0);
    public static final Color DANGER = new Color(0xDC, 0x26, 0x26);
    public static final Color DANGER_SOFT = new Color(0xFD, 0xEC, 0xEC);
    public static final Color SLATE = new Color(0x6B, 0x72, 0x7F);
    public static final Color SLATE_SOFT = new Color(0xF0, 0xF1, 0xF3);

    public static final Color HERO_TOP = new Color(0x5A, 0x0E, 0x13);
    public static final Color HERO_MID = new Color(0x9F, 0x18, 0x20);
    public static final Color HERO_GLOW = new Color(0xF0, 0x56, 0x5D);

    public static final Color TABLE_HEADER_BG = new Color(0xF7, 0xF9, 0xFC);
    public static final Color TABLE_ALT_ROW = new Color(0xFB, 0xFC, 0xFE);
    public static final Color TABLE_SELECTION = new Color(0xFC, 0xE8, 0xE9);

    public static final int RADIUS_CARD = 16;
    public static final int RADIUS_FIELD = 11;
    public static final int RADIUS_BUTTON = 11;
    public static final int SIDEBAR_WIDTH = 242;

    private static final String FONT_NAME = detectFont();

    public static final Font XS = font(11f, Font.PLAIN);
    public static final Font SM = font(12f, Font.PLAIN);
    public static final Font SM_BOLD = font(12f, Font.BOLD);
    public static final Font BODY = font(13f, Font.PLAIN);
    public static final Font BODY_BOLD = font(13f, Font.BOLD);
    public static final Font MD = font(14f, Font.PLAIN);
    public static final Font MD_BOLD = font(14f, Font.BOLD);
    public static final Font LG_BOLD = font(17f, Font.BOLD);
    public static final Font XL_BOLD = font(22f, Font.BOLD);
    public static final Font HERO = font(34f, Font.BOLD);
    public static final Font EYEBROW = font(11f, Font.BOLD);

    private PortalTheme() {}

    public static Font font(float size, int style) {
        return new Font(FONT_NAME, style, Math.round(size));
    }

    public static Color statusForeground(EventStatus status) {
        return switch (status) {
            case APPROVED -> SUCCESS;
            case PENDING_APPROVAL -> WARN;
            case REJECTED -> DANGER;
            case CANCELLED -> SLATE;
        };
    }

    public static Color statusBackground(EventStatus status) {
        return switch (status) {
            case APPROVED -> SUCCESS_SOFT;
            case PENDING_APPROVAL -> WARN_SOFT;
            case REJECTED -> DANGER_SOFT;
            case CANCELLED -> SLATE_SOFT;
        };
    }

    private static String detectFont() {
        String[] preferred = {"SF Pro Text", "SF Pro Display", "Helvetica Neue", "Segoe UI",
            "Inter", "Roboto", "Dialog"};
        String[] installed;
        try {
            installed = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        } catch (RuntimeException exception) {
            return "Dialog";
        }
        for (String candidate : preferred) {
            for (String name : installed) {
                if (name.equalsIgnoreCase(candidate)) {
                    return candidate;
                }
            }
        }
        return "Dialog";
    }
}
