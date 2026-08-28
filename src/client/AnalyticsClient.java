package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class AnalyticsClient {
    private final String host;
    private final int port;
    private final String username;
    private final String authMode;
    private final String password;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private AnalyticsDashboardUI ui;

    private String overviewJson = "{}";
    private String seriesJson = "{}";
    private String engagementJson = "{}";
    private String groupHealthJson = "{}";
    private String userJson = "{}";
    private String groupJson = "{}";

    public AnalyticsClient(String host, int port, String username, String authMode, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.authMode = authMode;
        this.password = password;
    }

    public void start() throws IOException {
        connectAndAuthenticate();
        try {
            SwingUtilities.invokeAndWait(() -> {
                ui = new AnalyticsDashboardUI(this, username);
                ui.buildUI();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while initializing analytics UI", e);
        } catch (InvocationTargetException e) {
            throw new IOException("Failed to initialize analytics UI", e);
        }

        new Thread(this::readLoop, "analytics-reader").start();
        requestRefresh("24h");
    }

    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public void requestRefresh(String window) {
        String w = normalizeWindow(window);
        out.println("/analytics_overview " + w);
        out.println("/analytics_series messages_total " + w + " hour");
        out.println("/analytics_engagement " + w);
        out.println("/analytics_group_health " + w);
    }

    public void requestUserDrilldown(String targetUsername, String window) {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            return;
        }
        out.println("/analytics_user " + targetUsername.trim() + " " + normalizeWindow(window));
    }

    public void requestGroupDrilldown(String groupIdText, String window) {
        if (groupIdText == null || groupIdText.trim().isEmpty()) {
            return;
        }
        out.println("/analytics_group " + groupIdText.trim() + " " + normalizeWindow(window));
    }

    String getOverviewJson() {
        return overviewJson;
    }

    String getSeriesJson() {
        return seriesJson;
    }

    String getEngagementJson() {
        return engagementJson;
    }

    String getGroupHealthJson() {
        return groupHealthJson;
    }

    String getUserJson() {
        return userJson;
    }

    String getGroupJson() {
        return groupJson;
    }

    private void connectAndAuthenticate() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        String serverLine = in.readLine();
        if (serverLine == null || !serverLine.startsWith("AUTH_REQUIRED")) {
            throw new IOException("Server did not request authentication.");
        }

        String command = ("register".equalsIgnoreCase(authMode) ? "REGISTER" : "LOGIN")
            + " " + username + " " + password;
        out.println(command);

        while (true) {
            String response = in.readLine();
            if (response == null) {
                throw new IOException("Authentication failed.");
            }
            if (response.startsWith("DATA_")) {
                continue;
            }
            if (response.startsWith("AUTH_OK")) {
                return;
            }
            if (response.startsWith("AUTH_FAIL")) {
                throw new IOException(response);
            }
        }
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("DATA_ANALYTICS_ACCESS_DENIED")) {
                    publishStatus("Analytics access denied for user: " + username);
                    continue;
                }
                if (line.startsWith("DATA_ANALYTICS_OVERVIEW")) {
                    overviewJson = payload(line, "DATA_ANALYTICS_OVERVIEW");
                    publishUpdate();
                    continue;
                }
                if (line.startsWith("DATA_ANALYTICS_SERIES")) {
                    seriesJson = payload(line, "DATA_ANALYTICS_SERIES");
                    publishUpdate();
                    continue;
                }
                if (line.startsWith("DATA_ANALYTICS_ENGAGEMENT")) {
                    engagementJson = payload(line, "DATA_ANALYTICS_ENGAGEMENT");
                    publishUpdate();
                    continue;
                }
                if (line.startsWith("DATA_ANALYTICS_GROUP_HEALTH")) {
                    groupHealthJson = payload(line, "DATA_ANALYTICS_GROUP_HEALTH");
                    publishUpdate();
                    continue;
                }
                if (line.startsWith("DATA_ANALYTICS_USER")) {
                    userJson = payload(line, "DATA_ANALYTICS_USER");
                    publishUpdate();
                    continue;
                }
                if (line.startsWith("DATA_ANALYTICS_GROUP")) {
                    groupJson = payload(line, "DATA_ANALYTICS_GROUP");
                    publishUpdate();
                }
            }
        } catch (IOException e) {
            publishStatus("Connection closed.");
        } finally {
            close();
        }
    }

    private void publishUpdate() {
        if (ui == null) {
            return;
        }
        SwingUtilities.invokeLater(ui::refreshView);
    }

    private void publishStatus(String status) {
        if (ui == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> ui.setStatus(status));
    }

    private String payload(String line, String prefix) {
        return line.length() > prefix.length() ? line.substring(prefix.length()).trim() : "{}";
    }

    private String normalizeWindow(String window) {
        if (window == null || window.trim().isEmpty()) {
            return "24h";
        }
        return window.trim();
    }

    public static void main(String[] args) throws IOException {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Theme.applyToUI();

        String host = "localhost";
        int port = 12345;
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        ChatClientAuth.AuthInput auth = ChatClientAuth.promptAuth();
        AnalyticsClient client = new AnalyticsClient(host, port, auth.username, auth.mode, auth.password);
        client.start();
    }
}
