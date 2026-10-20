package edu.mec.studentportal.client;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Minimal Swing entry point; Member 8 builds the actual navigation and screens. */
public final class ClientApplication {
    private ClientApplication() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Student Portal with Event Management System");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setSize(900, 600);
            frame.setLocationRelativeTo(null);
            frame.setLayout(new BorderLayout());
            frame.add(new JLabel("Student Portal — UI scaffold", SwingConstants.CENTER), BorderLayout.CENTER);
            frame.setVisible(true);
        });
    }
}
