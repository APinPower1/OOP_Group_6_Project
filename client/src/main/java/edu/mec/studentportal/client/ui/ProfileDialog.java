package edu.mec.studentportal.client.ui;

import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.api.ApiTask;
import edu.mec.studentportal.client.api.SessionContext;
import edu.mec.studentportal.client.dto.ChangePasswordRequest;
import edu.mec.studentportal.client.dto.UserSummary;
import edu.mec.studentportal.client.ui.theme.Glyph;
import edu.mec.studentportal.client.ui.theme.PortalTheme;
import edu.mec.studentportal.client.ui.theme.UiKit;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.charset.StandardCharsets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Profile + password dialog with two modes:
 * <ul>
 *   <li><b>Normal</b> — opened from the topbar: identity read-out plus change-password.</li>
 *   <li><b>Forced</b> — opened right after login when {@code mustChangePassword} is set:
 *       modal, no close button, only "Set password" or "Log out" escape. There is no
 *       email reset flow — admins reset in person.</li>
 * </ul>
 */
public final class ProfileDialog extends JDialog {
    private final transient ApiClient api;
    private final transient SessionContext session;
    private final boolean forced;

    private final JLabel mustChangeNote;
    private JPanel headerPanel;
    private final JPasswordField currentField = new JPasswordField(22);
    private final JPasswordField newField = new JPasswordField(22);
    private final JPasswordField confirmField = new JPasswordField(22);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton saveButton;

    private ProfileDialog(Window owner, ApiClient api, SessionContext session,
        boolean forced) {
        super(owner, forced ? "Set a new password" : "Profile & security",
            ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.session = session;
        this.forced = forced;

        setDefaultCloseOperation(forced ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
        setResizable(false);

        this.saveButton = forced
            ? UiKit.gradientButton("Set password")
            : UiKit.gradientButton("Change password");
        saveButton.addActionListener(event -> save());

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(PortalTheme.CARD);
        root.setBorder(new EmptyBorder(20, 22, 18, 22));

        mustChangeNote = buildHeader();
        root.add(headerPanel, BorderLayout.NORTH);
        root.add(buildForm(), BorderLayout.CENTER);
        root.add(buildButtons(), BorderLayout.SOUTH);

        getRootPane().setDefaultButton(saveButton);
        if (!forced) {
            getRootPane().registerKeyboardAction(
                event -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }
        setContentPane(root);
        pack();
        setSize(Math.max(getWidth(), 420), getHeight());
        setLocationRelativeTo(owner);
    }

    /** Opens the normal profile dialog (blocks while modal). */
    public static void showProfile(Component owner, ApiClient api,
        SessionContext session) {
        open(owner, api, session, false);
    }

    /** Opens the forced first-login dialog (blocks until password set or logout). */
    public static void showForced(Component owner, ApiClient api,
        SessionContext session) {
        open(owner, api, session, true);
    }

    private static void open(Component owner, ApiClient api, SessionContext session,
        boolean forced) {
        if (session == null || session.getUser() == null) {
            return;
        }
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        ProfileDialog dialog = new ProfileDialog(window, api, session, forced);
        dialog.setVisible(true);
    }

    private JLabel buildHeader() {
        UserSummary user = session.getUser();
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        info.setOpaque(false);
        info.add(UiKit.avatar(user.fullName(), 40));
        JPanel texts = new JPanel(new java.awt.GridLayout(0, 1, 0, 1));
        texts.setOpaque(false);
        JLabel name = new JLabel(user.fullName());
        name.setFont(PortalTheme.MD_BOLD);
        name.setForeground(PortalTheme.INK);
        JLabel meta = new JLabel(user.email() + "  \u00b7  " + user.role()
            + "  \u00b7  ID " + user.id());
        meta.setFont(PortalTheme.XS);
        meta.setForeground(PortalTheme.MUTED);
        texts.add(name);
        texts.add(meta);
        info.add(texts);

        JLabel note = banner(user);

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(info, BorderLayout.NORTH);
        north.add(note, BorderLayout.SOUTH);
        headerPanel = north;
        return note;
    }

    private JLabel banner(UserSummary user) {
        JLabel note;
        if (forced) {
            note = new JLabel("<html><b>Your account is using a temporary password.</b>"
                + "<br>Set a new password to continue. There is no email reset \u2014 "
                + "your admin can re-issue a temporary password in person.</html>");
            note.setForeground(PortalTheme.WARN);
        } else if (user.mustChangePassword()) {
            note = new JLabel("<html><b>Password change required.</b> Set a new "
                + "password before continuing.</html>");
            note.setForeground(PortalTheme.WARN);
        } else {
            note = new JLabel("Change your password below \u2014 the current one is "
                + "required.");
            note.setForeground(PortalTheme.MUTED);
        }
        note.setFont(PortalTheme.SM);
        note.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PortalTheme.BORDER),
            new EmptyBorder(8, 10, 8, 10)));
        note.setOpaque(true);
        note.setBackground(forced || user.mustChangePassword()
            ? PortalTheme.WARN_SOFT : PortalTheme.SLATE_SOFT);
        return note;
    }

    private JPanel buildForm() {
        JPanel form = UiKit.vstack(10, true);
        form.add(UiKit.fieldStack("Current password", currentField));
        form.add(UiKit.fieldStack("New password (8–72 characters)", newField));
        form.add(UiKit.fieldStack("Confirm new password", confirmField));
        statusLabel.setFont(PortalTheme.SM);
        statusLabel.setForeground(PortalTheme.MUTED);
        form.add(statusLabel);
        UiKit.enableTextEditing(currentField);
        UiKit.enableTextEditing(newField);
        UiKit.enableTextEditing(confirmField);
        return form;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        if (forced) {
            JButton logout = UiKit.ghostButton("Log out", Glyph.LOGOUT);
            logout.addActionListener(event -> {
                dispose();
                session.clearSession();
            });
            buttons.add(logout);
        } else {
            JButton close = UiKit.ghostButton("Close");
            close.addActionListener(event -> dispose());
            buttons.add(close);
        }
        buttons.add(saveButton);
        return buttons;
    }

    private void save() {
        String current = new String(currentField.getPassword());
        String next = new String(newField.getPassword());
        String confirm = new String(confirmField.getPassword());
        if (current.isEmpty()) {
            fail("Current password is required.");
            return;
        }
        if (next.isEmpty()) {
            fail("New password is required.");
            return;
        }
        if (next.getBytes(StandardCharsets.UTF_8).length < 8
            || next.getBytes(StandardCharsets.UTF_8).length > 72) {
            fail("New password must be between 8 and 72 UTF-8 bytes.");
            return;
        }
        if (!next.equals(confirm)) {
            fail("New passwords do not match.");
            return;
        }
        statusLabel.setForeground(PortalTheme.MUTED);
        statusLabel.setText("Saving…");
        saveButton.setEnabled(false);
        ApiTask.run(
            () -> api.post("/auth/change-password",
                new ChangePasswordRequest(current, next),
                edu.mec.studentportal.client.dto.UserSummaryResponse.class),
            response -> {
                saveButton.setEnabled(true);
                if (response == null) {
                    fail("Unexpected empty response.");
                    return;
                }
                try {
                    session.refreshUser(response.toUserSummary());
                } catch (RuntimeException ignored) {
                    // signed out while the call was in flight — nothing to refresh
                }
                currentField.setText("");
                newField.setText("");
                confirmField.setText("");
                statusLabel.setForeground(PortalTheme.SUCCESS);
                statusLabel.setText("Password changed.");
                if (forced) {
                    dispose();
                } else {
                    mustChangeNote.setText("Password changed \u2014 you're all set.");
                    mustChangeNote.setForeground(PortalTheme.SUCCESS);
                    mustChangeNote.setBackground(PortalTheme.SUCCESS_SOFT);
                }
            },
            exception -> {
                saveButton.setEnabled(true);
                fail(exception.getMessage());
            }
        );
    }

    private void fail(String message) {
        statusLabel.setForeground(PortalTheme.DANGER);
        statusLabel.setText(message == null || message.isBlank()
            ? "Password change failed." : message);
    }
}
