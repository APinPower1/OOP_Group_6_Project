package edu.mec.studentportal.client.ui.theme;

import edu.mec.studentportal.client.dto.EventStatus;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * Design tokens for the refreshed UI — "Campus Ink": green-tinted paper canvas, deep
 * forest sidebar, all-emerald accent system with amber reserved for warnings only. Deliberately avoids the
 * blue/violet gradient look. Fonts resolve to the best installed modern UI face (SF Pro
 * on macOS, Segoe UI on Windows) with a safe logical fallback everywhere else.
 */
public final class PortalTheme {
    public static final Color SIDEBAR_TOP = new Color(0x0E, 0x2C, 0x1D);
    public static final Color SIDEBAR_BOTTOM = new Color(0x1A, 0x51, 0x38);
    public static final Color SIDEBAR_TEXT = new Color(0xCD, 0xE9, 0xD8);
    public static final Color SIDEBAR_TEXT_DIM = new Color(0x8F, 0xBB, 0xA3);
    public static final Color SIDEBAR_HOVER = new Color(0x2C, 0x62, 0x48);

    public static final Color CANVAS = new Color(0xEC, 0xF4, 0xEA);
    public static final Color CARD = Color.WHITE;
    public static final Color INK = new Color(0x14, 0x25, 0x1C);
    public static final Color MUTED = new Color(0x5C, 0x7A, 0x69);
    public static final Color BORDER = new Color(0xD5, 0xE4, 0xD3);
    public static final Color HAIRLINE = new Color(0xDF, 0xED, 0xE0);

    public static final Color ACCENT = new Color(0x04, 0x8A, 0x54);
    public static final Color ACCENT_2 = new Color(0x0A, 0xA9, 0x6C);
    public static final Color ACCENT_SOFT = new Color(0xDD, 0xF6, 0xE9);
    public static final Color CYAN = new Color(0x4F, 0xEA, 0xC0);

    public static final Color SUCCESS = new Color(0x15, 0x80, 0x3D);
    public static final Color SUCCESS_SOFT = new Color(0xE9, 0xF6, 0xEE);
    public static final Color WARN = new Color(0xB4, 0x53, 0x09);
    public static final Color WARN_SOFT = new Color(0xFD, 0xF2, 0xE0);
    public static final Color DANGER = new Color(0xDC, 0x26, 0x26);
    public static final Color DANGER_SOFT = new Color(0xFD, 0xEC, 0xEC);
    public static final Color SLATE = new Color(0x6E, 0x7B, 0x74);
    public static final Color SLATE_SOFT = new Color(0xED, 0xEE, 0xEA);

    public static final Color HERO_TOP = new Color(0x0A, 0x21, 0x1A);
    public static final Color HERO_MID = new Color(0x0F, 0x42, 0x34);
    public static final Color HERO_GLOW = new Color(0x0D, 0xBF, 0x77);

    public static final Color TABLE_HEADER_BG = new Color(0xED, 0xF5, 0xEE);
    public static final Color TABLE_ALT_ROW = new Color(0xF4, 0xFA, 0xF5);
    public static final Color TABLE_SELECTION = new Color(0xD7, 0xF3, 0xE3);

    public static final int RADIUS_CARD = 16;
    public static final int RADIUS_FIELD = 11;
    public static final int RADIUS_BUTTON = 11;
    public static final int SIDEBAR_WIDTH = 232;

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
