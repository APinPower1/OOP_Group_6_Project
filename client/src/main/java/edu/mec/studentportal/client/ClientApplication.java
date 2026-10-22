package edu.mec.studentportal.client;

import edu.mec.studentportal.client.api.ClientConfig;
import edu.mec.studentportal.client.ui.MainFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Desktop entry point: loads client configuration (API base URL/timeouts only — no database
 * settings), then shows the main frame on the Event Dispatch Thread.
 */
public final class ClientApplication {
    private ClientApplication() {}

    public static void main(String[] args) {
        ClientConfig config;
        try {
            config = ClientConfig.load();
        } catch (RuntimeException exception) {
            System.err.println("Client configuration error: " + exception.getMessage());
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                    null,
                    "Client configuration error:\n" + exception.getMessage(),
                    "Student Portal",
                    JOptionPane.ERROR_MESSAGE
                )
            );
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exception) {
            System.out.println("System look and feel unavailable; using default.");
        }

        System.out.println("Student Portal client → API base URL: " + config.baseUrl());
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(config);
            frame.setSize(1180, 760);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
