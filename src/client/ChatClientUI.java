package client;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;

public class ChatClientUI {
    private static final Color BG = Theme.BG;
    private static final Color SIDEBAR = Theme.SIDEBAR;
    private static final Color ACCENT = Theme.ACCENT;
    private static final Color INK = Theme.INK;
    private static final Color MUTED = Theme.MUTED;
    private static final Color BORDER = Theme.BORDER;
    private static final Color BUBBLE_IN = Theme.BUBBLE_IN;
    private static final Color BUBBLE_OUT = Theme.BUBBLE_OUT;
    private static final Color SYSTEM_BUBBLE = Theme.BUBBLE_SYSTEM;
    private static final Font UI_FONT = Theme.UI_FONT;
    private static final Font HEADER_FONT = Theme.HEADER_FONT;

    private ChatClient client;

    public JFrame frame;
    public JPanel messagesPanel;
    public JPanel notificationsPanel;
    public JScrollPane scrollPane;
    public JScrollPane notificationsScroll;
    public JTextArea inputField;
    public JButton sendButton;
    public JPanel composerPanel;

    private JPanel contactsPanel;
    private JPanel contextPanel;
    private JLabel threadTitle;
    private JLabel threadSubtitle;
    private JLabel emptyStateTitle;
    private JButton addMemberAction;
    private JButton promoteAction;
    private JButton removeAction;
    private JButton leaveAction;
    private JButton removeDmAction;
    private Map<String, JButton> contactButtons = new HashMap<>();
    private Map<Integer, JButton> groupButtons = new HashMap<>();

    public ChatClientUI(ChatClient client) {
        this.client = client;
    }

    public void buildUI() {
        frame = new JFrame("Echo");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(1120, 740);
        frame.setMinimumSize(new Dimension(900, 620));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.close();
            }
        });

        frame.add(buildMainArea(), BorderLayout.CENTER);
        updateComposerForChat();
        updateThreadHeader();

        appendSystemMessage("Connected to " + client.host + ":" + client.port + " as " + client.username + ".");
        appendSystemMessage("Select a direct message or group to start chatting.");
        frame.setVisible(true);
    }

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.add(buildSidebar(), BorderLayout.WEST);
        main.add(buildThreadArea(), BorderLayout.CENTER);
        main.add(buildRightRail(), BorderLayout.EAST);
        return main;
    }

    private JPanel buildSidebar() {
        JPanel sidebarShell = new JPanel(new BorderLayout());
        sidebarShell.setPreferredSize(new Dimension(288, 0));
        sidebarShell.setBackground(SIDEBAR);
        sidebarShell.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        JPanel header = new JPanel(new BorderLayout(10, 10));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(22, 18, 14, 18));

        JPanel account = new JPanel();
        account.setOpaque(false);
        account.setLayout(new BoxLayout(account, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Echo");
        title.setFont(Theme.TITLE_FONT);
        title.setForeground(INK);
        JLabel user = new JLabel("Signed in as " + client.username);
        user.setFont(Theme.SMALL_FONT);
        user.setForeground(MUTED);
        account.add(title);
        account.add(Box.createVerticalStrut(4));
        account.add(user);

        header.add(account, BorderLayout.WEST);
        header.add(pill("Online", Theme.SUCCESS, new Color(232, 248, 239)), BorderLayout.EAST);

        contactsPanel = new JPanel();
        contactsPanel.setLayout(new BoxLayout(contactsPanel, BoxLayout.Y_AXIS));
        contactsPanel.setBackground(SIDEBAR);
        contactsPanel.setBorder(new EmptyBorder(0, 16, 18, 16));
        refreshSidebar();

        JScrollPane contactsScroll = new JScrollPane(contactsPanel);
        contactsScroll.setBorder(BorderFactory.createEmptyBorder());
        contactsScroll.getViewport().setBackground(SIDEBAR);
        contactsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contactsScroll.getVerticalScrollBar().setUnitIncrement(14);

        sidebarShell.add(header, BorderLayout.NORTH);
        sidebarShell.add(contactsScroll, BorderLayout.CENTER);
        return sidebarShell;
    }

    private JPanel buildThreadArea() {
        JPanel threadPanel = new JPanel(new BorderLayout());
        threadPanel.setBackground(BG);
        threadPanel.add(buildThreadHeader(), BorderLayout.NORTH);

        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(BG);
        messagesPanel.setBorder(new EmptyBorder(20, 28, 20, 28));

        scrollPane = new JScrollPane(messagesPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(BG);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        composerPanel = new JPanel(new BorderLayout());
        composerPanel.setBackground(BG);

        threadPanel.add(scrollPane, BorderLayout.CENTER);
        threadPanel.add(composerPanel, BorderLayout.SOUTH);
        return threadPanel;
    }

    private JPanel buildThreadHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setBackground(Theme.PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(18, 24, 18, 24)
        ));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        threadTitle = new JLabel("Choose a conversation");
        threadTitle.setFont(HEADER_FONT);
        threadTitle.setForeground(INK);
        threadSubtitle = new JLabel("Your messages will appear here.");
        threadSubtitle.setFont(Theme.SMALL_FONT);
        threadSubtitle.setForeground(MUTED);
        copy.add(threadTitle);
        copy.add(Box.createVerticalStrut(4));
        copy.add(threadSubtitle);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        addMemberAction = iconButton("+ Member");
        addMemberAction.addActionListener(e -> addGroupMember());
        promoteAction = iconButton("Promote");
        promoteAction.addActionListener(e -> promoteGroupOwner());
        removeAction = iconButton("Remove");
        removeAction.addActionListener(e -> removeGroupMember());
        leaveAction = iconButton("Leave");
        leaveAction.addActionListener(e -> leaveCurrentGroup());
        removeDmAction = iconButton("Remove DM");
        removeDmAction.addActionListener(e -> removeCurrentContact());
        actions.add(addMemberAction);
        actions.add(promoteAction);
        actions.add(removeAction);
        actions.add(leaveAction);
        actions.add(removeDmAction);

        header.add(copy, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildRightRail() {
        JPanel rightRail = new JPanel(new BorderLayout());
        rightRail.setPreferredSize(new Dimension(264, 0));
        rightRail.setBackground(Theme.PANEL);
        rightRail.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(22, 18, 10, 18));

        JLabel title = new JLabel("Details");
        title.setFont(HEADER_FONT);
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Members and activity");
        subtitle.setFont(Theme.SMALL_FONT);
        subtitle.setForeground(MUTED);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);

        contextPanel = new JPanel();
        contextPanel.setLayout(new BoxLayout(contextPanel, BoxLayout.Y_AXIS));
        contextPanel.setBackground(Theme.PANEL);
        contextPanel.setBorder(new EmptyBorder(0, 16, 12, 16));

        notificationsPanel = new JPanel();
        notificationsPanel.setLayout(new BoxLayout(notificationsPanel, BoxLayout.Y_AXIS));
        notificationsPanel.setBackground(Theme.PANEL);
        notificationsPanel.setBorder(new EmptyBorder(10, 16, 18, 16));

        notificationsScroll = new JScrollPane(notificationsPanel);
        notificationsScroll.setBorder(BorderFactory.createEmptyBorder());
        notificationsScroll.getViewport().setBackground(Theme.PANEL);
        notificationsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        notificationsScroll.getVerticalScrollBar().setUnitIncrement(14);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(contextPanel, BorderLayout.CENTER);

        rightRail.add(top, BorderLayout.NORTH);
        rightRail.add(notificationsScroll, BorderLayout.CENTER);
        refreshContextPanel();
        return rightRail;
    }

    private void updateComposerForChat() {
        composerPanel.removeAll();
        composerPanel.setLayout(new BorderLayout());
        composerPanel.setBorder(new EmptyBorder(12, 28, 22, 28));
        composerPanel.setBackground(BG);

        RoundedPanel composerInner = new RoundedPanel(22, Theme.PANEL, new Color(205, 216, 230));
        composerInner.setLayout(new BorderLayout(12, 0));
        composerInner.setBorder(new EmptyBorder(10, 12, 10, 10));

        inputField = new JTextArea(2, 20);
        inputField.setFont(UI_FONT);
        inputField.setForeground(INK);
        inputField.setBackground(Theme.PANEL);
        inputField.setCaretColor(INK);
        inputField.setLineWrap(true);
        inputField.setWrapStyleWord(true);
        inputField.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        inputField.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "sendMessage");
        inputField.getActionMap().put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.messageHandler.sendMessage();
            }
        });
        inputField.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");

        sendButton = Theme.primaryButton("Send");
        sendButton.setPreferredSize(new Dimension(78, 38));
        sendButton.addActionListener(e -> client.messageHandler.sendMessage());

        boolean canSend =
            (client.currentTargetType == ChatClient.ChatTargetType.DM && client.currentContact != null)
                || (client.currentTargetType == ChatClient.ChatTargetType.GROUP && client.currentGroupId != null);
        inputField.setEnabled(canSend);
        sendButton.setEnabled(canSend);
        sendButton.setBackground(canSend ? ACCENT : new Color(185, 195, 208));

        if (client.currentTargetType == ChatClient.ChatTargetType.GROUP && client.currentGroupId != null) {
            String groupName = client.groups.get(client.currentGroupId);
            inputField.setToolTipText("Message #" + (groupName != null ? groupName : client.currentGroupId));
        } else if (client.currentTargetType == ChatClient.ChatTargetType.DM && client.currentContact != null) {
            inputField.setToolTipText("Message " + client.currentContact);
        } else {
            inputField.setToolTipText("Select a contact or group first");
        }

        JScrollPane inputScroll = new JScrollPane(inputField);
        inputScroll.setBorder(BorderFactory.createEmptyBorder());
        inputScroll.getViewport().setBackground(Theme.PANEL);
        inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        inputScroll.setPreferredSize(new Dimension(0, 62));

        composerInner.add(inputScroll, BorderLayout.CENTER);
        composerInner.add(sendButton, BorderLayout.EAST);
        composerPanel.add(composerInner, BorderLayout.CENTER);
        composerPanel.revalidate();
        composerPanel.repaint();
    }

    public void appendIncomingMessage(String message) {
        SwingUtilities.invokeLater(() -> addBubble(message, BubbleType.INCOMING));
    }

    public void appendOutgoingMessage(String message) {
        SwingUtilities.invokeLater(() -> addBubble(message, BubbleType.OUTGOING));
    }

    public void appendSystemMessage(String message) {
        SwingUtilities.invokeLater(() -> addNotificationBubble(message));
    }

    public void appendAgentMessage(String message) {
        SwingUtilities.invokeLater(() -> addBubble(message, BubbleType.AGENT));
    }

    private void addBubble(String message, BubbleType type) {
        hideEmptyState();
        Bubble bubble = new Bubble(message, type);
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(2, 0, 2, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        if (type == BubbleType.OUTGOING) {
            row.add(bubble, BorderLayout.EAST);
        } else {
            row.add(bubble, BorderLayout.WEST);
        }
        messagesPanel.add(row);
        messagesPanel.add(Box.createVerticalStrut(4));
        messagesPanel.revalidate();
        scrollChatToBottom();
    }

    private void addNotificationBubble(String message) {
        Bubble bubble = new Bubble(message, BubbleType.SYSTEM);
        bubble.setAlignmentX(Component.LEFT_ALIGNMENT);
        notificationsPanel.add(bubble);
        notificationsPanel.add(Box.createVerticalStrut(10));
        notificationsPanel.revalidate();
        scrollNotificationsToBottom();
    }

    private void scrollChatToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void scrollNotificationsToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = notificationsScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void selectContact(String contact) {
        client.currentTargetType = ChatClient.ChatTargetType.DM;
        client.currentContact = contact;
        client.currentGroupId = null;
        messagesPanel.removeAll();
        showEmptyState("Loading conversation", "Fetching your direct message history.");
        refreshSidebar();
        updateThreadHeader();
        refreshContextPanel();

        client.loadingHistory = true;
        client.out.println("/dm_history " + contact);

        messagesPanel.revalidate();
        messagesPanel.repaint();

        updateComposerForChat();
        inputField.requestFocus();

        appendSystemMessage("Chatting with " + contact);
    }

    private void selectGroup(int groupId) {
        client.currentTargetType = ChatClient.ChatTargetType.GROUP;
        client.currentGroupId = groupId;
        client.currentContact = null;
        messagesPanel.removeAll();
        showEmptyState("Loading group", "Fetching recent messages and member roles.");
        refreshSidebar();
        updateThreadHeader();
        refreshContextPanel();

        client.loadingGroupHistory = true;
        client.out.println("/g_history " + groupId);
        client.out.println("/group_members " + groupId);
        client.utils.requestAgentCatalog(groupId);

        messagesPanel.revalidate();
        messagesPanel.repaint();

        updateComposerForChat();
        inputField.requestFocus();

        String name = client.groups.get(groupId);
        appendSystemMessage("Group: " + (name != null ? name : ("#" + groupId)));
    }

    private void addNewContact() {
        String newContact = showUserPicker("New direct message", "Search registered users");
        if (newContact == null) {
            return;
        }
        if (client.contacts.contains(newContact)) {
            JOptionPane.showMessageDialog(frame, "Already chatting with " + newContact);
            return;
        }

        client.out.println("/add_dm " + newContact);
        selectContact(newContact);
    }

    private String showUserPicker(String title, String placeholder) {
        final String[] selected = new String[1];
        JDialog dialog = new JDialog(frame, title, true);
        dialog.setSize(380, 440);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(Theme.PANEL);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(Theme.PANEL);
        content.setBorder(new EmptyBorder(18, 18, 14, 18));

        JTextField search = new JTextField();
        search.setFont(UI_FONT);
        search.setForeground(INK);
        search.setBackground(Theme.PANEL_ELEVATED);
        search.setCaretColor(INK);
        search.setToolTipText(placeholder);
        search.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(10, 12, 10, 12)
        ));

        DefaultListModel<String> model = new DefaultListModel<>();
        JList<String> results = new JList<>(model);
        results.setFont(UI_FONT);
        results.setForeground(INK);
        results.setBackground(Theme.PANEL);
        results.setSelectionBackground(Theme.ACCENT_SOFT);
        results.setSelectionForeground(ACCENT);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JLabel helper = new JLabel("Type at least one letter, then select a result.");
        helper.setFont(Theme.SMALL_FONT);
        helper.setForeground(MUTED);

        JScrollPane listPane = new JScrollPane(results);
        listPane.setBorder(BorderFactory.createLineBorder(BORDER));

        JButton add = Theme.primaryButton("Add");
        add.setEnabled(false);
        add.setBackground(new Color(185, 195, 208));
        JButton cancel = Theme.secondaryButton("Cancel");

        results.addListSelectionListener(e -> {
            String value = results.getSelectedValue();
            boolean hasSelection = value != null && !value.trim().isEmpty();
            add.setEnabled(hasSelection);
            add.setBackground(hasSelection ? ACCENT : new Color(185, 195, 208));
            helper.setText(hasSelection ? "Ready to add " + value + "." : "Select a result to enable Add.");
            helper.setForeground(hasSelection ? Theme.SUCCESS : MUTED);
        });

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }

            private void refresh() {
                String query = search.getText().trim();
                model.clear();
                add.setEnabled(false);
                add.setBackground(new Color(185, 195, 208));
                if (query.isEmpty()) {
                    helper.setText("Type at least one letter, then select a result.");
                    helper.setForeground(MUTED);
                    return;
                }
                helper.setText("Searching...");
                helper.setForeground(MUTED);
                new Thread(() -> {
                    List<String> matches = client.utils.searchUsers(query);
                    SwingUtilities.invokeLater(() -> {
                        if (!query.equals(search.getText().trim())) {
                            return;
                        }
                        model.clear();
                        for (String match : matches) {
                            model.addElement(match);
                        }
                        if (model.isEmpty()) {
                            helper.setText("No matching registered users found.");
                            helper.setForeground(Theme.WARN);
                        } else {
                            helper.setText("Select a result to enable Add.");
                            helper.setForeground(MUTED);
                        }
                    });
                }, "user-search").start();
            }
        });

        add.addActionListener(e -> {
            String value = results.getSelectedValue();
            if (value == null || value.trim().isEmpty()) {
                helper.setText("Select a result before adding.");
                helper.setForeground(Theme.WARN);
                return;
            }
            selected[0] = value;
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(add);

        content.add(search, BorderLayout.NORTH);
        content.add(listPane, BorderLayout.CENTER);
        content.add(helper, BorderLayout.SOUTH);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.setVisible(true);
        return selected[0];
    }

    private String showMemberPicker(String title, boolean allowSelf) {
        if (client.currentGroupId == null) {
            return null;
        }
        List<String> members = client.groupMembers.get(client.currentGroupId);
        if (members == null || members.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Member list is still loading.");
            return null;
        }

        final String[] selected = new String[1];
        JDialog dialog = new JDialog(frame, title, true);
        dialog.setSize(360, 420);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(Theme.PANEL);

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length < 2) {
                continue;
            }
            if (!allowSelf && parts[0].equals(client.username)) {
                continue;
            }
            model.addElement(parts[0] + "  " + parts[1]);
        }

        JList<String> list = new JList<>(model);
        list.setFont(UI_FONT);
        list.setForeground(INK);
        list.setBackground(Theme.PANEL);
        list.setSelectionBackground(Theme.ACCENT_SOFT);
        list.setSelectionForeground(ACCENT);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JLabel helper = new JLabel("Select a member to continue.");
        helper.setFont(Theme.SMALL_FONT);
        helper.setForeground(MUTED);

        JButton choose = Theme.primaryButton("Select");
        choose.setEnabled(false);
        choose.setBackground(new Color(185, 195, 208));
        JButton cancel = Theme.secondaryButton("Cancel");

        list.addListSelectionListener(e -> {
            boolean hasSelection = list.getSelectedValue() != null;
            choose.setEnabled(hasSelection);
            choose.setBackground(hasSelection ? ACCENT : new Color(185, 195, 208));
        });
        choose.addActionListener(e -> {
            String value = list.getSelectedValue();
            if (value == null) {
                helper.setText("Select a member first.");
                helper.setForeground(Theme.WARN);
                return;
            }
            selected[0] = value.trim().split("\\s+", 2)[0];
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(Theme.PANEL);
        content.setBorder(new EmptyBorder(18, 18, 14, 18));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        content.add(scroll, BorderLayout.CENTER);
        content.add(helper, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancel);
        actions.add(choose);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.setVisible(true);
        return selected[0];
    }

    public void showAgentsDialog() {
        if (client.currentTargetType != ChatClient.ChatTargetType.GROUP || client.currentGroupId == null) {
            JOptionPane.showMessageDialog(frame, "Open a group chat before using /agents.");
            return;
        }
        client.utils.waitForAgentCatalog(client.currentGroupId);

        JDialog dialog = new JDialog(frame, "Agents", true);
        dialog.setSize(520, 440);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(Theme.PANEL);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.PANEL);
        content.setBorder(new EmptyBorder(18, 18, 12, 18));

        JLabel title = new JLabel("Add agents to this group");
        title.setFont(HEADER_FONT);
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Mention added agents with @product-manager or @program-manager.");
        subtitle.setFont(Theme.SMALL_FONT);
        subtitle.setForeground(MUTED);
        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(16));

        if (client.availableAgents.isEmpty()) {
            content.add(railHint("No agents are available from the server yet."));
        } else {
            for (AgentInfo agent : client.availableAgents.values()) {
                content.add(agentCard(agent, dialog));
                content.add(Box.createVerticalStrut(10));
            }
        }

        JButton close = Theme.secondaryButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(0, 18, 14, 18));
        footer.add(close);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.PANEL);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JPanel agentCard(AgentInfo agent, JDialog dialog) {
        RoundedPanel card = new RoundedPanel(16, Theme.PANEL_ELEVATED, BORDER);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(agent.displayName);
        name.setFont(Theme.UI_FONT_BOLD);
        name.setForeground(INK);
        JLabel role = new JLabel("<html><body style='width:300px'>" + agent.role + "</body></html>");
        role.setFont(Theme.SMALL_FONT);
        role.setForeground(MUTED);
        copy.add(name);
        copy.add(Box.createVerticalStrut(5));
        copy.add(role);

        boolean added = isAgentInCurrentGroup(agent.id);
        JButton action = added ? Theme.secondaryButton("Remove") : Theme.primaryButton("Add");
        action.setPreferredSize(new Dimension(92, 36));
        action.addActionListener(e -> {
            if (client.currentGroupId == null) {
                return;
            }
            if (isAgentInCurrentGroup(agent.id)) {
                client.out.println("/group_remove_agent " + client.currentGroupId + " " + agent.id);
            } else {
                client.out.println("/group_add_agent " + client.currentGroupId + " " + agent.id);
            }
            client.utils.waitForAgentCatalog(client.currentGroupId);
            refreshContextPanel();
            dialog.dispose();
            showAgentsDialog();
        });

        card.add(avatar("AI"), BorderLayout.WEST);
        card.add(copy, BorderLayout.CENTER);
        card.add(action, BorderLayout.EAST);
        return card;
    }

    private void createGroup() {
        String groupName = JOptionPane.showInputDialog(
            frame,
            "Enter group name:",
            "Create Group",
            JOptionPane.PLAIN_MESSAGE
        );
        if (groupName == null || groupName.trim().isEmpty()) {
            return;
        }
        client.out.println("/group_create " + groupName.trim());
    }

    private void addGroupMember() {
        if (client.currentGroupId == null) {
            JOptionPane.showMessageDialog(frame, "Select a group first.");
            return;
        }
        if (!isCurrentUserOwner(client.currentGroupId)) {
            JOptionPane.showMessageDialog(frame, "Only group owners can add members.");
            return;
        }
        String username = showUserPicker("Add group member", "Search registered users");
        if (username == null) {
            return;
        }
        client.out.println("/group_add " + client.currentGroupId + " " + username);
    }

    private void promoteGroupOwner() {
        if (client.currentGroupId == null) {
            JOptionPane.showMessageDialog(frame, "Select a group first.");
            return;
        }
        if (!isCurrentUserOwner(client.currentGroupId)) {
            JOptionPane.showMessageDialog(frame, "Only group owners can promote owners.");
            return;
        }
        String username = showMemberPicker("Promote group owner", false);
        if (username == null) {
            return;
        }
        client.out.println("/group_promote_owner " + client.currentGroupId + " " + username);
    }

    private void removeGroupMember() {
        if (client.currentGroupId == null) {
            JOptionPane.showMessageDialog(frame, "Select a group first.");
            return;
        }
        if (!isCurrentUserOwner(client.currentGroupId)) {
            JOptionPane.showMessageDialog(frame, "Only group owners can remove members.");
            return;
        }
        String username = showMemberPicker("Remove group member", true);
        if (username == null) {
            return;
        }
        client.out.println("/group_remove " + client.currentGroupId + " " + username);
    }

    private void showGroupMembersDialog() {
        if (client.currentGroupId == null) {
            JOptionPane.showMessageDialog(frame, "Select a group first.");
            return;
        }

        client.out.println("/group_members " + client.currentGroupId);

        List<String> members = client.groupMembers.get(client.currentGroupId);
        if (members == null || members.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "No member data available yet.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length < 2) {
                continue;
            }
            sb.append(parts[0]).append(" (").append(parts[1]).append(")\n");
        }

        String groupName = client.groups.get(client.currentGroupId);
        String title = "Group Members - " + (groupName != null ? groupName : ("#" + client.currentGroupId));
        JTextArea area = new JTextArea(sb.toString().trim());
        area.setEditable(false);
        area.setFont(UI_FONT);
        area.setBackground(Theme.PANEL);
        area.setForeground(INK);
        area.setBorder(new EmptyBorder(12, 12, 12, 12));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JScrollPane pane = new JScrollPane(area);
        pane.setPreferredSize(new Dimension(320, 220));

        JOptionPane.showMessageDialog(frame, pane, title, JOptionPane.PLAIN_MESSAGE);
    }

    private void leaveCurrentGroup() {
        if (client.currentGroupId == null) {
            JOptionPane.showMessageDialog(frame, "Select a group first.");
            return;
        }
        client.out.println("/group_leave " + client.currentGroupId);
    }

    private void removeCurrentContact() {
        if (client.currentTargetType != ChatClient.ChatTargetType.DM || client.currentContact == null) {
            JOptionPane.showMessageDialog(frame, "Select a direct message first.");
            return;
        }
        String contact = client.currentContact;
        int result = JOptionPane.showConfirmDialog(
            frame,
            "Remove " + contact + " from your direct messages?",
            "Remove Direct Message",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        client.out.println("/remove_dm " + contact);
        client.contacts.remove(contact);
        client.currentTargetType = ChatClient.ChatTargetType.NONE;
        client.currentContact = null;
        messagesPanel.removeAll();
        refreshSidebar();
        updateThreadHeader();
        refreshContextPanel();
        updateComposerForChat();
    }

    public void addContactButton(String contact) {
        if (contactButtons.containsKey(contact)) {
            return;
        }

        boolean active = client.currentTargetType == ChatClient.ChatTargetType.DM && contact.equals(client.currentContact);
        JButton btn = navButton("DM", contact, active);
        btn.addActionListener(e -> selectContact(contact));

        contactButtons.put(contact, btn);
        contactsPanel.add(btn);
        contactsPanel.add(Box.createVerticalStrut(6));
        contactsPanel.revalidate();
    }

    public void addGroupButton(int groupId, String groupName) {
        if (groupButtons.containsKey(groupId)) {
            return;
        }

        boolean active = client.currentTargetType == ChatClient.ChatTargetType.GROUP
            && client.currentGroupId != null
            && client.currentGroupId.equals(groupId);
        JButton btn = navButton("#", groupName, active);
        btn.setToolTipText("Group ID " + groupId);
        btn.addActionListener(e -> selectGroup(groupId));

        groupButtons.put(groupId, btn);
        contactsPanel.add(btn);
        contactsPanel.add(Box.createVerticalStrut(6));
        contactsPanel.revalidate();
    }

    public void refreshContacts(List<String> contacts) {
        refreshSidebar();
    }

    public void refreshSidebar() {
        if (contactsPanel == null) {
            return;
        }
        contactButtons.clear();
        groupButtons.clear();
        contactsPanel.removeAll();

        addSidebarControls();

        addSectionLabel("Direct Messages", client.contacts.size());
        for (String contact : client.contacts) {
            addContactButton(contact);
        }
        if (client.contacts.isEmpty()) {
            addSidebarHint("No contacts yet.");
        }

        contactsPanel.add(Box.createVerticalStrut(12));
        addSectionLabel("Groups", client.groups.size());
        for (Map.Entry<Integer, String> entry : client.groups.entrySet()) {
            addGroupButton(entry.getKey(), entry.getValue());
        }
        if (client.groups.isEmpty()) {
            addSidebarHint("No groups yet.");
        }

        contactsPanel.add(Box.createVerticalGlue());
        contactsPanel.revalidate();
        contactsPanel.repaint();
    }

    private void addSidebarControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton addContactBtn = compactButton("+ DM", true);
        addContactBtn.addActionListener(e -> addNewContact());
        JButton createGroupBtn = compactButton("+ Group", false);
        createGroupBtn.addActionListener(e -> createGroup());
        controls.add(addContactBtn);
        controls.add(createGroupBtn);
        contactsPanel.add(controls);

        contactsPanel.add(Box.createVerticalStrut(18));
    }

    private void addSectionLabel(String text, int count) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(new EmptyBorder(4, 2, 7, 2));

        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        label.setForeground(MUTED);
        JLabel number = new JLabel(String.valueOf(count));
        number.setFont(Theme.SMALL_FONT);
        number.setForeground(Theme.MUTED_LIGHT);
        row.add(label, BorderLayout.WEST);
        row.add(number, BorderLayout.EAST);
        contactsPanel.add(row);
    }

    private void addSidebarHint(String text) {
        JLabel hint = new JLabel(text);
        hint.setFont(Theme.SMALL_FONT);
        hint.setForeground(Theme.MUTED_LIGHT);
        hint.setBorder(new EmptyBorder(8, 4, 8, 4));
        contactsPanel.add(hint);
    }

    private JButton navButton(String token, String label, boolean active) {
        JButton button = new JButton(token + "  " + label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(active ? Theme.UI_FONT_BOLD : UI_FONT);
        button.setForeground(active ? ACCENT : INK);
        button.setBackground(active ? Theme.ACCENT_SOFT : SIDEBAR);
        button.setBorder(new EmptyBorder(10, 12, 10, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return button;
    }

    private JButton compactButton(String text, boolean primary) {
        JButton button = primary ? Theme.primaryButton(text) : Theme.secondaryButton(text);
        button.setPreferredSize(new Dimension(primary ? 86 : 100, 36));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton toolButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.SMALL_FONT);
        button.setForeground(MUTED);
        button.setBackground(new Color(229, 233, 239));
        button.setBorder(new EmptyBorder(7, 9, 7, 9));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton iconButton(String text) {
        JButton button = Theme.secondaryButton(text);
        button.setFont(Theme.SMALL_FONT);
        button.setPreferredSize(new Dimension(88, 34));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JLabel pill(String text, Color foreground, Color background) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
        label.setForeground(foreground);
        label.setBackground(background);
        label.setOpaque(true);
        label.setBorder(new EmptyBorder(6, 10, 6, 10));
        return label;
    }

    private JLabel avatar(String value) {
        String initial = value == null || value.isEmpty() ? "?" : value.substring(0, 1).toUpperCase();
        JLabel avatar = new JLabel(initial, SwingConstants.CENTER);
        avatar.setFont(Theme.UI_FONT_BOLD);
        avatar.setForeground(ACCENT);
        avatar.setOpaque(true);
        avatar.setBackground(Theme.ACCENT_SOFT);
        avatar.setPreferredSize(new Dimension(30, 30));
        avatar.setBorder(BorderFactory.createLineBorder(new Color(202, 213, 255)));
        return avatar;
    }

    private void updateThreadHeader() {
        if (threadTitle == null || threadSubtitle == null) {
            return;
        }
        if (client.currentTargetType == ChatClient.ChatTargetType.DM && client.currentContact != null) {
            threadTitle.setText(client.currentContact);
            threadSubtitle.setText("Direct message");
            setHeaderActions(false, false, false, false, true);
        } else if (client.currentTargetType == ChatClient.ChatTargetType.GROUP && client.currentGroupId != null) {
            String name = client.groups.get(client.currentGroupId);
            threadTitle.setText("# " + (name != null ? name : client.currentGroupId));
            List<String> members = client.groupMembers.get(client.currentGroupId);
            int count = members == null ? 0 : members.size();
            threadSubtitle.setText(count > 0 ? count + " member" + (count == 1 ? "" : "s") : "Group conversation");
            boolean owner = isCurrentUserOwner(client.currentGroupId);
            setHeaderActions(owner, owner, owner, true, false);
        } else {
            threadTitle.setText("Choose a conversation");
            threadSubtitle.setText("Your messages will appear here.");
            setHeaderActions(false, false, false, false, false);
            showEmptyState("Welcome to Echo", "Pick a direct message or group from the sidebar.");
        }
    }

    private void setHeaderActions(boolean addVisible, boolean promoteVisible, boolean removeVisible, boolean leaveVisible, boolean removeDmVisible) {
        if (addMemberAction == null) {
            return;
        }
        addMemberAction.setVisible(addVisible);
        promoteAction.setVisible(promoteVisible);
        removeAction.setVisible(removeVisible);
        leaveAction.setVisible(leaveVisible);
        removeDmAction.setVisible(removeDmVisible);
    }

    public void refreshContextPanel() {
        if (contextPanel == null) {
            return;
        }
        contextPanel.removeAll();

        if (client.currentTargetType == ChatClient.ChatTargetType.GROUP && client.currentGroupId != null) {
            JLabel label = new JLabel("MEMBERS");
            label.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
            label.setForeground(MUTED);
            label.setBorder(new EmptyBorder(4, 0, 8, 0));
            contextPanel.add(label);

            List<String> members = client.groupMembers.get(client.currentGroupId);
            if (members == null || members.isEmpty()) {
                contextPanel.add(railHint("Member list loading."));
            } else {
                for (String entry : members) {
                    String[] parts = entry.split(":", 2);
                    if (parts.length < 2) {
                        continue;
                    }
                    contextPanel.add(memberRow(parts[0], parts[1]));
                    contextPanel.add(Box.createVerticalStrut(6));
                }
            }
            contextPanel.add(Box.createVerticalStrut(14));
            JLabel agentsLabel = new JLabel("AGENTS");
            agentsLabel.setFont(Theme.SMALL_FONT.deriveFont(Font.BOLD));
            agentsLabel.setForeground(MUTED);
            agentsLabel.setBorder(new EmptyBorder(4, 0, 8, 0));
            contextPanel.add(agentsLabel);

            List<String> agentIds = client.groupAgents.get(client.currentGroupId);
            if (agentIds == null || agentIds.isEmpty()) {
                contextPanel.add(railHint("Type /agents to add AI teammates."));
            } else {
                for (String agentId : agentIds) {
                    AgentInfo agent = client.availableAgents.get(agentId);
                    if (agent != null) {
                        contextPanel.add(agentRow(agent));
                        contextPanel.add(Box.createVerticalStrut(6));
                    }
                }
            }
        } else if (client.currentTargetType == ChatClient.ChatTargetType.DM && client.currentContact != null) {
            contextPanel.add(profileBlock(client.currentContact, "Direct message"));
            contextPanel.add(Box.createVerticalStrut(10));
            contextPanel.add(railHint("Use Remove DM in the thread header to clear this person from your sidebar."));
        } else {
            contextPanel.add(railHint("Open a conversation to see people and actions here."));
        }

        contextPanel.revalidate();
        contextPanel.repaint();
        updateThreadHeader();
    }

    private JPanel agentRow(AgentInfo agent) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(agent.displayName);
        name.setFont(Theme.UI_FONT_BOLD);
        name.setForeground(INK);
        JLabel handle = new JLabel("@" + agent.id);
        handle.setFont(Theme.SMALL_FONT);
        handle.setForeground(MUTED);
        copy.add(name);
        copy.add(handle);
        row.add(avatar("AI"), BorderLayout.WEST);
        row.add(copy, BorderLayout.CENTER);
        row.add(pill("AI", ACCENT, Theme.ACCENT_SOFT), BorderLayout.EAST);
        return row;
    }

    private JPanel memberRow(String username, String role) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JLabel avatar = avatar(username);
        JLabel name = new JLabel(username);
        name.setFont(Theme.UI_FONT_BOLD);
        name.setForeground(INK);
        JLabel roleLabel = new JLabel(role);
        roleLabel.setFont(Theme.SMALL_FONT);
        roleLabel.setForeground("owner".equalsIgnoreCase(role) ? ACCENT : MUTED);
        row.add(avatar, BorderLayout.WEST);
        row.add(name, BorderLayout.CENTER);
        row.add(roleLabel, BorderLayout.EAST);
        return row;
    }

    private JPanel profileBlock(String username, String subtitle) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(username);
        name.setFont(Theme.UI_FONT_BOLD);
        name.setForeground(INK);
        JLabel sub = new JLabel(subtitle);
        sub.setFont(Theme.SMALL_FONT);
        sub.setForeground(MUTED);
        copy.add(name);
        copy.add(sub);
        row.add(avatar(username), BorderLayout.WEST);
        row.add(copy, BorderLayout.CENTER);
        return row;
    }

    private JLabel railHint(String text) {
        JLabel hint = new JLabel("<html><body style='width:190px'>" + text + "</body></html>");
        hint.setFont(Theme.SMALL_FONT);
        hint.setForeground(Theme.MUTED_LIGHT);
        hint.setBorder(new EmptyBorder(4, 0, 8, 0));
        return hint;
    }

    private void showEmptyState(String title, String subtitle) {
        if (messagesPanel == null) {
            return;
        }
        messagesPanel.removeAll();
        JPanel state = new JPanel();
        state.setOpaque(false);
        state.setLayout(new BoxLayout(state, BoxLayout.Y_AXIS));
        state.setAlignmentX(Component.CENTER_ALIGNMENT);
        state.setBorder(new EmptyBorder(120, 20, 20, 20));

        emptyStateTitle = new JLabel(title);
        emptyStateTitle.setFont(Theme.TITLE_FONT);
        emptyStateTitle.setForeground(INK);
        emptyStateTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel emptyStateSubtitle = new JLabel(subtitle);
        emptyStateSubtitle.setFont(UI_FONT);
        emptyStateSubtitle.setForeground(MUTED);
        emptyStateSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        state.add(emptyStateTitle);
        state.add(Box.createVerticalStrut(8));
        state.add(emptyStateSubtitle);
        messagesPanel.add(Box.createVerticalGlue());
        messagesPanel.add(state);
        messagesPanel.add(Box.createVerticalGlue());
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    private void hideEmptyState() {
        if (emptyStateTitle != null) {
            messagesPanel.removeAll();
            emptyStateTitle = null;
        }
    }

    private boolean isCurrentUserOwner(int groupId) {
        List<String> members = client.groupMembers.get(groupId);
        if (members == null) {
            return false;
        }
        for (String entry : members) {
            String[] parts = entry.split(":", 2);
            if (parts.length < 2) {
                continue;
            }
            if (parts[0].equals(client.username) && "owner".equalsIgnoreCase(parts[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean isAgentInCurrentGroup(String agentId) {
        if (client.currentGroupId == null || agentId == null) {
            return false;
        }
        List<String> agents = client.groupAgents.get(client.currentGroupId);
        return agents != null && agents.contains(agentId);
    }

    private enum BubbleType {
        INCOMING,
        OUTGOING,
        AGENT,
        SYSTEM
    }

    private static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color fill;
        private final Color stroke;

        RoundedPanel(int arc, Color fill, Color stroke) {
            this.arc = arc;
            this.fill = fill;
            this.stroke = stroke;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(stroke);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class Bubble extends JPanel {
        private final BubbleType type;
        private final JTextArea textArea;

        Bubble(String text, BubbleType type) {
            this.type = type;
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(3, 3, 3, 3));

            textArea = new JTextArea(text);
            textArea.setFont(type == BubbleType.SYSTEM ? Theme.SMALL_FONT : UI_FONT);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setEditable(false);
            textArea.setOpaque(false);
            textArea.setColumns(type == BubbleType.SYSTEM ? 24 : 48);
            if (type == BubbleType.OUTGOING) {
                textArea.setForeground(Color.WHITE);
            } else if (type == BubbleType.AGENT) {
                textArea.setForeground(new Color(40, 45, 63));
            } else if (type == BubbleType.SYSTEM) {
                textArea.setForeground(MUTED);
            } else {
                textArea.setForeground(INK);
            }

            JPanel inner = new JPanel(new BorderLayout());
            inner.setOpaque(false);
            inner.setBorder(new EmptyBorder(11, 14, 11, 14));
            inner.add(textArea, BorderLayout.CENTER);
            add(inner, BorderLayout.CENTER);

            int maxWidth = type == BubbleType.SYSTEM ? 226 : 640;
            setMaximumSize(new Dimension(maxWidth, Short.MAX_VALUE));
        }

        @Override
        public Dimension getPreferredSize() {
            int maxWidth = type == BubbleType.SYSTEM ? 226 : 640;
            Insets insets = getInsets();
            int textWidth = maxWidth - insets.left - insets.right - 34;
            textArea.setSize(new Dimension(textWidth, Short.MAX_VALUE));
            Dimension preferred = super.getPreferredSize();
            return new Dimension(Math.min(preferred.width, maxWidth), preferred.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int arc = type == BubbleType.SYSTEM ? 14 : 18;
            int width = getWidth() - 2;
            int height = getHeight() - 2;

            if (type == BubbleType.OUTGOING) {
                g2.setColor(BUBBLE_OUT);
            } else if (type == BubbleType.AGENT) {
                g2.setColor(new Color(246, 249, 255));
            } else if (type == BubbleType.SYSTEM) {
                g2.setColor(SYSTEM_BUBBLE);
            } else {
                g2.setColor(BUBBLE_IN);
            }
            g2.fillRoundRect(0, 0, width, height, arc, arc);
            g2.setColor(type == BubbleType.OUTGOING ? BUBBLE_OUT : new Color(213, 222, 235));
            g2.drawRoundRect(0, 0, width, height, arc, arc);
            g2.dispose();
        }
    }
}
