package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.lang.reflect.InvocationTargetException;

public class ChatClient {
    public final String host;
    public final int port;
    public String username;
    public String authMode;
    public String password;

    public Socket socket;
    public BufferedReader in;
    public PrintWriter out;

    public List<String> contacts = new ArrayList<>();
    public Map<Integer, String> groups = new LinkedHashMap<>();
    public Map<Integer, List<String>> groupMembers = new LinkedHashMap<>();
    public Map<String, AgentInfo> availableAgents = new LinkedHashMap<>();
    public Map<Integer, List<String>> groupAgents = new LinkedHashMap<>();
    public ChatTargetType currentTargetType = ChatTargetType.NONE;
    public String currentContact;
    public Integer currentGroupId;
    public volatile boolean userExistsResponse = false;
    public volatile String lastValidationRequest = null;
    public volatile String lastUserSearchRequest = null;
    public final Object userSearchLock = new Object();
    public List<String> userSearchResults = new ArrayList<>();
    public volatile Integer lastAgentCatalogRequest = null;
    public final Object agentCatalogLock = new Object();
    public boolean loadingHistory = false;
    public List<String> pendingHistory = new ArrayList<>();
    public boolean loadingGroupHistory = false;
    public List<String> pendingGroupHistory = new ArrayList<>();

    public ChatClientAuth auth;
    public ChatClientUtils utils;
    public ChatClientUI ui;
    public ChatClientMessage messageHandler;

    public ChatClient(String host, int port, String username, String authMode, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.authMode = authMode;
        this.password = password;
        this.auth = new ChatClientAuth(this);
        this.utils = new ChatClientUtils(this);
        this.ui = new ChatClientUI(this);
        this.messageHandler = new ChatClientMessage(this);
    }

    public void start() throws IOException {
        auth.connect();
        try {
            SwingUtilities.invokeAndWait(() -> ui.buildUI());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while initializing UI", e);
        } catch (InvocationTargetException e) {
            throw new IOException("Failed to initialize UI", e);
        }
        new Thread(() -> messageHandler.readLoop(), "chat-reader").start();

        // after we start listening to server messages, request the stored contact list
        utils.requestContacts();
        utils.requestGroups();
    }

    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) throws IOException {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Theme.applyToUI();

        String host = "localhost";
        int port = 12345;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);

        ChatClientAuth.AuthInput auth = ChatClientAuth.promptAuth();
        ChatClient client = new ChatClient(host, port, auth.username, auth.mode, auth.password);
        client.start();
    }

    public enum ChatTargetType {
        NONE,
        DM,
        GROUP
    }
}
