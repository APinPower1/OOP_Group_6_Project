package edu.mec.studentportal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.mec.studentportal.client.api.ApiClient;
import edu.mec.studentportal.client.auth.SessionContext;
import edu.mec.studentportal.client.ui.PortalFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class ClientApplication {
    private ClientApplication() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            configureLookAndFeel();
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
            SessionContext session = new SessionContext();
            ApiClient apiClient = new ApiClient(objectMapper, session::token);
            new PortalFrame(apiClient, session).setVisible(true);
        });
    }

    private static void configureLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException exception) {
            throw new IllegalStateException("Unable to configure the desktop look and feel.", exception);
        }
    }
}
