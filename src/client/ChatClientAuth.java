package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class ChatClientAuth {
    private final ChatClient client;

    public ChatClientAuth(ChatClient client) {
        this.client = client;
    }

    public void connect() throws IOException {
        client.socket = new Socket(client.host, client.port);
        client.in = new BufferedReader(new InputStreamReader(client.socket.getInputStream()));
        client.out = new PrintWriter(client.socket.getOutputStream(), true);

        String serverLine = client.in.readLine();
        if (serverLine == null || !serverLine.startsWith("AUTH_REQUIRED")) {
            throw new IOException("Server did not request authentication.");
        }

        String command = ("register".equalsIgnoreCase(client.authMode) ? "REGISTER" : "LOGIN")
            + " " + client.username + " " + client.password;
        client.out.println(command);

        String response;
        while (true) {
            response = client.in.readLine();
            if (response == null) {
                throw new IOException("Authentication failed.");
            }
            if (response.startsWith("DATA_")) {
                continue;
            }
            if (response.startsWith("AUTH_OK")) {
                break;
            }
            if (response.startsWith("AUTH_FAIL")) {
                throw new IOException(response);
            }
        }
    }

    public static AuthInput promptAuth() {
        while (true) {
            JTextField userField = new JTextField(18);
            JPasswordField passField = new JPasswordField(18);
            styleAuthField(userField);
            styleAuthField(passField);

            JPanel fields = new JPanel(new GridLayout(2, 1, 0, 10));
            fields.setOpaque(false);
            fields.add(fieldBlock("Username", userField));
            fields.add(fieldBlock("Password", passField));

            JPanel panel = new JPanel(new BorderLayout(0, 16));
            panel.setBackground(Theme.PANEL);
            panel.setBorder(new EmptyBorder(8, 8, 4, 8));

            JPanel intro = new JPanel();
            intro.setOpaque(false);
            intro.setLayout(new BoxLayout(intro, BoxLayout.Y_AXIS));
            JLabel title = new JLabel("Welcome to Echo");
            title.setFont(Theme.TITLE_FONT);
            title.setForeground(Theme.INK);
            JLabel subtitle = new JLabel("Sign in or create an account to continue.");
            subtitle.setFont(Theme.UI_FONT);
            subtitle.setForeground(Theme.MUTED);
            intro.add(title);
            intro.add(Box.createVerticalStrut(4));
            intro.add(subtitle);

            panel.add(intro, BorderLayout.NORTH);
            panel.add(fields, BorderLayout.CENTER);

            String[] options = {"Login", "Register", "Cancel"};
            int modeChoice = JOptionPane.showOptionDialog(
                null,
                panel,
                "Echo",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
            );
            if (modeChoice == 2 || modeChoice == JOptionPane.CLOSED_OPTION) {
                System.exit(0);
            }

            String user = userField.getText().trim();
            if (user.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Username is required.");
                continue;
            }

            String pass = new String(passField.getPassword()).trim();
            if (pass.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Password is required.");
                continue;
            }

            AuthInput input = new AuthInput();
            input.username = user;
            input.password = pass;
            input.mode = modeChoice == 1 ? "register" : "login";
            return input;
        }
    }

    private static JPanel fieldBlock(String labelText, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(Theme.UI_FONT_BOLD);
        label.setForeground(Theme.MUTED);
        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private static void styleAuthField(JTextField field) {
        field.setPreferredSize(new Dimension(280, 38));
        field.setFont(Theme.UI_FONT);
        field.setForeground(Theme.INK);
        field.setBackground(Theme.PANEL_ELEVATED);
        field.setCaretColor(Theme.INK);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER),
            new EmptyBorder(8, 10, 8, 10)
        ));
    }

    public static class AuthInput {
        String username;
        String password;
        String mode;
    }
}
