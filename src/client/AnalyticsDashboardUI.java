package client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.border.EmptyBorder;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnalyticsDashboardUI {
    private static final Color BG = Theme.BG;
    private static final Color CARD = Theme.PANEL_ELEVATED;
    private static final Color INK = Theme.INK;
    private static final Color MUTED = Theme.MUTED;
    private static final Color TABLE_BG = new Color(26, 30, 38);
    private static final Color TABLE_ALT = new Color(30, 35, 46);
    private static final Color TABLE_GRID = new Color(52, 60, 74);

    private final AnalyticsClient client;
    private final String username;

    private JFrame frame;
    private JLabel statusLabel;
    private JComboBox<String> windowSelector;
    private JTextField userField;
    private JTextField groupField;
    private JTabbedPane tabs;

    private DefaultTableModel overviewModel;
    private DefaultTableModel seriesSummaryModel;
    private DefaultTableModel seriesPointsModel;
    private DefaultTableModel engagementSummaryModel;
    private DefaultTableModel engagementTrendModel;
    private DefaultTableModel healthSummaryModel;
    private DefaultTableModel healthTrendModel;
    private DefaultTableModel userSummaryModel;
    private DefaultTableModel userTrendModel;
    private DefaultTableModel groupSummaryModel;
    private DefaultTableModel groupMessageTrendModel;
    private DefaultTableModel groupMemberTrendModel;
    private DefaultTableModel groupRecentModel;

    public AnalyticsDashboardUI(AnalyticsClient client, String username) {
        this.client = client;
        this.username = username;
    }

    public void buildUI() {
        frame = new JFrame("Analytics Dashboard Client");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(1100, 760);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout());

        frame.add(buildTopBar(), BorderLayout.NORTH);
        frame.add(buildTabs(), BorderLayout.CENTER);
        frame.add(buildStatusBar(), BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    public void refreshView() {
        updateOverview(client.getOverviewJson());
        updateSeries(client.getSeriesJson());
        updateEngagement(client.getEngagementJson());
        updateGroupHealth(client.getGroupHealthJson());
        updateUser(client.getUserJson());
        updateGroup(client.getGroupJson());
        setStatus("Analytics updated.");
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    private JPanel buildTopBar() {
        JPanel top = Theme.gradientHeader();
        top.setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel("Analytics Dashboard");
        title.setForeground(INK);
        title.setFont(Theme.TITLE_FONT);

        JPanel controls = new JPanel(new GridLayout(2, 1, 8, 8));
        controls.setBackground(new Color(0, 0, 0, 0));
        controls.add(buildRefreshRow());
        controls.add(buildDrilldownRow());

        top.add(title, BorderLayout.WEST);
        top.add(controls, BorderLayout.CENTER);
        return top;
    }

    private JPanel buildRefreshRow() {
        JPanel row = new JPanel();
        row.setBackground(new Color(0, 0, 0, 0));
        row.setLayout(new BorderLayout(8, 8));

        JPanel left = new JPanel();
        left.setBackground(new Color(0, 0, 0, 0));
        left.setLayout(new BorderLayout(8, 8));

        windowSelector = new JComboBox<>(new String[] {"24h", "7d", "30d"});
        windowSelector.setBackground(Theme.PANEL_ELEVATED);
        windowSelector.setForeground(INK);
        windowSelector.setFont(Theme.UI_FONT);
        windowSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setBackground(isSelected ? new Color(52, 93, 120) : Theme.PANEL_ELEVATED);
                c.setForeground(Color.WHITE);
                return c;
            }
        });

        JButton refresh = Theme.primaryButton("Refresh");
        refresh.addActionListener(e -> {
            String window = (String) windowSelector.getSelectedItem();
            client.requestRefresh(window);
            setStatus("Requested refresh for " + window + ".");
        });

        JLabel label = new JLabel("Window", SwingConstants.LEFT);
        label.setForeground(MUTED);
        label.setFont(Theme.UI_FONT_BOLD);
        left.add(label, BorderLayout.WEST);
        left.add(windowSelector, BorderLayout.CENTER);
        left.add(refresh, BorderLayout.EAST);

        row.add(left, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildDrilldownRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 8, 8));
        row.setBackground(new Color(0, 0, 0, 0));

        JPanel userPanel = new JPanel(new BorderLayout(8, 8));
        userPanel.setBackground(new Color(0, 0, 0, 0));
        userField = new JTextField();
        userField.setBackground(Theme.PANEL_ELEVATED);
        userField.setForeground(INK);
        userField.setCaretColor(INK);
        userField.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        userField.setFont(Theme.UI_FONT);

        JButton userBtn = Theme.secondaryButton("Load User Drilldown");
        userBtn.addActionListener(e -> {
            String target = userField.getText().trim();
            if (target.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Enter a username.");
                return;
            }
            client.requestUserDrilldown(target, (String) windowSelector.getSelectedItem());
            setStatus("Requested user drilldown for " + target + ".");
        });
        userPanel.add(userField, BorderLayout.CENTER);
        userPanel.add(userBtn, BorderLayout.EAST);

        JPanel groupPanel = new JPanel(new BorderLayout(8, 8));
        groupPanel.setBackground(new Color(0, 0, 0, 0));
        groupField = new JTextField();
        groupField.setBackground(Theme.PANEL_ELEVATED);
        groupField.setForeground(INK);
        groupField.setCaretColor(INK);
        groupField.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        groupField.setFont(Theme.UI_FONT);

        JButton groupBtn = Theme.secondaryButton("Load Group Drilldown");
        groupBtn.addActionListener(e -> {
            String gid = groupField.getText().trim();
            if (gid.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Enter a group id.");
                return;
            }
            client.requestGroupDrilldown(gid, (String) windowSelector.getSelectedItem());
            setStatus("Requested group drilldown for #" + gid + ".");
        });
        groupPanel.add(groupField, BorderLayout.CENTER);
        groupPanel.add(groupBtn, BorderLayout.EAST);

        row.add(userPanel);
        row.add(groupPanel);
        return row;
    }

    private JTabbedPane buildTabs() {
        tabs = new JTabbedPane();
        tabs.setFont(Theme.UI_FONT_BOLD);
        tabs.setBackground(Theme.BG);
        tabs.setForeground(Theme.INK);
        tabs.addTab("Overview", buildOverviewTab());
        tabs.addTab("Series", buildSeriesTab());
        tabs.addTab("Engagement", buildEngagementTab());
        tabs.addTab("Group Health", buildGroupHealthTab());
        tabs.addTab("User Drilldown", buildUserTab());
        tabs.addTab("Group Drilldown", buildGroupTab());
        tabs.addChangeListener(e -> applyTabStyles());
        applyTabStyles();
        return tabs;
    }

    private void applyTabStyles() {
        if (tabs == null) {
            return;
        }
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (i == tabs.getSelectedIndex()) {
                tabs.setBackgroundAt(i, Color.WHITE);
                tabs.setForegroundAt(i, Color.BLACK);
            } else {
                tabs.setBackgroundAt(i, Theme.BG);
                tabs.setForegroundAt(i, Color.WHITE);
            }
        }
    }

    private JPanel buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(CARD);
        status.setBorder(new EmptyBorder(8, 12, 8, 12));

        statusLabel = new JLabel("Connected as " + username + ".");
        statusLabel.setForeground(INK);

        status.add(statusLabel, BorderLayout.WEST);
        return status;
    }

    private JPanel buildOverviewTab() {
        overviewModel = new DefaultTableModel(new String[] { "Metric", "Value" }, 0);
        JTable table = createTable(overviewModel);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.add(buildSection("Snapshot", table, 260), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSeriesTab() {
        seriesSummaryModel = new DefaultTableModel(new String[] { "Field", "Value" }, 0);
        seriesPointsModel = new DefaultTableModel(new String[] { "Bucket", "Count" }, 0);
        JTable summaryTable = createTable(seriesSummaryModel);
        JTable pointsTable = createTable(seriesPointsModel);

        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BorderLayout());
        panel.add(buildSection("Series Context", summaryTable, 170), BorderLayout.NORTH);
        panel.add(buildSection("Time Buckets", pointsTable, 420), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildEngagementTab() {
        engagementSummaryModel = new DefaultTableModel(new String[] { "Metric", "Value" }, 0);
        engagementTrendModel = new DefaultTableModel(new String[] { "Date", "Registrations" }, 0);
        JTable summaryTable = createTable(engagementSummaryModel);
        JTable trendTable = createTable(engagementTrendModel);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.add(buildSection("Engagement Snapshot", summaryTable, 200), BorderLayout.NORTH);
        panel.add(buildSection("Registration Trend", trendTable, 380), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildGroupHealthTab() {
        healthSummaryModel = new DefaultTableModel(new String[] { "Metric", "Value" }, 0);
        healthTrendModel = new DefaultTableModel(new String[] { "Date", "Membership Delta" }, 0);
        JTable summaryTable = createTable(healthSummaryModel);
        JTable trendTable = createTable(healthTrendModel);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.add(buildSection("Group Health Snapshot", summaryTable, 220), BorderLayout.NORTH);
        panel.add(buildSection("Membership Growth Trend", trendTable, 380), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildUserTab() {
        userSummaryModel = new DefaultTableModel(new String[] { "Metric", "Value" }, 0);
        userTrendModel = new DefaultTableModel(new String[] { "Date", "Messages" }, 0);
        JTable summaryTable = createTable(userSummaryModel);
        JTable trendTable = createTable(userTrendModel);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.add(buildSection("User Snapshot", summaryTable, 220), BorderLayout.NORTH);
        panel.add(buildSection("Message Trend", trendTable, 380), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildGroupTab() {
        groupSummaryModel = new DefaultTableModel(new String[] { "Metric", "Value" }, 0);
        groupMessageTrendModel = new DefaultTableModel(new String[] { "Date", "Messages" }, 0);
        groupMemberTrendModel = new DefaultTableModel(new String[] { "Date", "Member Delta" }, 0);
        groupRecentModel = new DefaultTableModel(new String[] { "Timestamp", "Event", "Actor" }, 0);

        JTable summaryTable = createTable(groupSummaryModel);
        JTable msgTrendTable = createTable(groupMessageTrendModel);
        JTable memberTrendTable = createTable(groupMemberTrendModel);
        JTable recentTable = createTable(groupRecentModel);

        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);
        top.add(buildSection("Group Snapshot", summaryTable, 220), BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setBackground(BG);
        center.setLayout(new BorderLayout());
        center.add(buildSection("Message Trend", msgTrendTable, 220), BorderLayout.NORTH);
        center.add(buildSection("Member Timeline", memberTrendTable, 220), BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(buildSection("Recent Activity", recentTable, 220), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSection(String title, JTable table, int preferredHeight) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(BG);
        section.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(Theme.UI_FONT_BOLD);
        label.setBorder(new EmptyBorder(0, 2, 8, 0));

        JScrollPane pane = new JScrollPane(table);
        pane.setBorder(BorderFactory.createLineBorder(TABLE_GRID));
        pane.setPreferredSize(new Dimension(300, preferredHeight));
        pane.getViewport().setBackground(TABLE_BG);

        section.add(label, BorderLayout.NORTH);
        section.add(pane, BorderLayout.CENTER);
        return section;
    }

    private JTable createTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setFont(Theme.UI_FONT);
        table.setForeground(INK);
        table.setBackground(TABLE_BG);
        table.getTableHeader().setFont(Theme.UI_FONT_BOLD);
        table.getTableHeader().setBackground(CARD);
        table.getTableHeader().setForeground(INK);
        table.setAutoCreateRowSorter(true);

        DefaultTableCellRenderer renderer = new StripeRenderer();
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        return table;
    }

    private void updateOverview(String json) {
        overviewModel.setRowCount(0);
        Map<String, Object> obj = SimpleJson.asObject(SimpleJson.parse(json));
        if (hasError(obj)) {
            addRow(overviewModel, "Error", SimpleJson.asString(obj.get("message")));
            return;
        }
        addRow(overviewModel, "Window", SimpleJson.asString(obj.get("window")));
        addRow(overviewModel, "Online Users", SimpleJson.asString(obj.get("online_users")));
        addRow(overviewModel, "Messages / Min", SimpleJson.asString(obj.get("messages_per_minute")));
        addRow(overviewModel, "Messages Total", SimpleJson.asString(obj.get("messages_total")));
        addRow(overviewModel, "DM Messages", SimpleJson.asString(obj.get("messages_dm")));
        addRow(overviewModel, "Group Messages", SimpleJson.asString(obj.get("messages_group")));
        addRow(overviewModel, "Active Groups", SimpleJson.asString(obj.get("active_groups")));
    }

    private void updateSeries(String json) {
        seriesSummaryModel.setRowCount(0);
        seriesPointsModel.setRowCount(0);
        Map<String, Object> obj = SimpleJson.asObject(SimpleJson.parse(json));
        if (hasError(obj)) {
            addRow(seriesSummaryModel, "Error", SimpleJson.asString(obj.get("message")));
            return;
        }
        addRow(seriesSummaryModel, "Metric", SimpleJson.asString(obj.get("metric")));
        addRow(seriesSummaryModel, "Window", SimpleJson.asString(obj.get("window")));
        addRow(seriesSummaryModel, "Bucket", SimpleJson.asString(obj.get("bucket")));

        List<Map<String, Object>> points = SimpleJson.asObjectList(obj.get("points"));
        for (Map<String, Object> point : points) {
            seriesPointsModel.addRow(new Object[] {
                SimpleJson.asString(point.get("x")),
                SimpleJson.asString(point.get("y"))
            });
        }
    }

    private void updateEngagement(String json) {
        engagementSummaryModel.setRowCount(0);
        engagementTrendModel.setRowCount(0);
        Map<String, Object> obj = SimpleJson.asObject(SimpleJson.parse(json));
        if (hasError(obj)) {
            addRow(engagementSummaryModel, "Error", SimpleJson.asString(obj.get("message")));
            return;
        }
        addRow(engagementSummaryModel, "Window", SimpleJson.asString(obj.get("window")));
        addRow(engagementSummaryModel, "DAU", SimpleJson.asString(obj.get("dau")));
        addRow(engagementSummaryModel, "WAU", SimpleJson.asString(obj.get("wau")));
        addRow(engagementSummaryModel, "New Registrations", SimpleJson.asString(obj.get("new_registrations")));

        List<Map<String, Object>> trend = SimpleJson.asObjectList(obj.get("registration_trend"));
        for (Map<String, Object> point : trend) {
            engagementTrendModel.addRow(new Object[] {
                SimpleJson.asString(point.get("x")),
                SimpleJson.asString(point.get("y"))
            });
        }
    }

    private void updateGroupHealth(String json) {
        healthSummaryModel.setRowCount(0);
        healthTrendModel.setRowCount(0);
        Map<String, Object> obj = SimpleJson.asObject(SimpleJson.parse(json));
        if (hasError(obj)) {
            addRow(healthSummaryModel, "Error", SimpleJson.asString(obj.get("message")));
            return;
        }
        addRow(healthSummaryModel, "Window", SimpleJson.asString(obj.get("window")));
        addRow(healthSummaryModel, "Total Groups", SimpleJson.asString(obj.get("total_groups")));
        addRow(healthSummaryModel, "New Groups", SimpleJson.asString(obj.get("new_groups")));
        addRow(healthSummaryModel, "Membership Growth", SimpleJson.asString(obj.get("membership_growth")));
        addRow(healthSummaryModel, "Owner / Member Ratio", SimpleJson.asString(obj.get("owner_member_ratio")));
        addRow(healthSummaryModel, "Inactive Groups", SimpleJson.asString(obj.get("inactive_groups")));

        List<Map<String, Object>> trend = SimpleJson.asObjectList(obj.get("membership_growth_trend"));
        for (Map<String, Object> point : trend) {
            healthTrendModel.addRow(new Object[] {
                SimpleJson.asString(point.get("x")),
                SimpleJson.asString(point.get("y"))
            });
        }
    }

    private void updateUser(String json) {
        userSummaryModel.setRowCount(0);
        userTrendModel.setRowCount(0);
        Map<String, Object> obj = SimpleJson.asObject(SimpleJson.parse(json));
        if (hasError(obj)) {
            addRow(userSummaryModel, "Error", SimpleJson.asString(obj.get("message")));
            return;
        }
        addRow(userSummaryModel, "Window", SimpleJson.asString(obj.get("window")));
        addRow(userSummaryModel, "Username", SimpleJson.asString(obj.get("username")));
        addRow(userSummaryModel, "DM Messages", SimpleJson.asString(obj.get("dm_messages")));
        addRow(userSummaryModel, "Group Messages", SimpleJson.asString(obj.get("group_messages")));
        addRow(userSummaryModel, "Groups", SimpleJson.joinArray(obj.get("groups")));

        List<Map<String, Object>> trend = SimpleJson.asObjectList(obj.get("message_trend"));
        for (Map<String, Object> point : trend) {
            userTrendModel.addRow(new Object[] {
                SimpleJson.asString(point.get("x")),
                SimpleJson.asString(point.get("y"))
            });
        }
    }

    private void updateGroup(String json) {
        groupSummaryModel.setRowCount(0);
        groupMessageTrendModel.setRowCount(0);
        groupMemberTrendModel.setRowCount(0);
        groupRecentModel.setRowCount(0);
        Map<String, Object> obj = SimpleJson.asObject(SimpleJson.parse(json));
        if (hasError(obj)) {
            addRow(groupSummaryModel, "Error", SimpleJson.asString(obj.get("message")));
            return;
        }
        addRow(groupSummaryModel, "Window", SimpleJson.asString(obj.get("window")));
        addRow(groupSummaryModel, "Group ID", SimpleJson.asString(obj.get("group_id")));
        addRow(groupSummaryModel, "Member Count", SimpleJson.asString(obj.get("member_count")));
        addRow(groupSummaryModel, "Message Count", SimpleJson.asString(obj.get("message_count")));
        addRow(groupSummaryModel, "Owner Changes", SimpleJson.asString(obj.get("owner_changes")));

        List<Map<String, Object>> msgTrend = SimpleJson.asObjectList(obj.get("message_trend"));
        for (Map<String, Object> point : msgTrend) {
            groupMessageTrendModel.addRow(new Object[] {
                SimpleJson.asString(point.get("x")),
                SimpleJson.asString(point.get("y"))
            });
        }

        List<Map<String, Object>> memberTrend = SimpleJson.asObjectList(obj.get("member_timeline"));
        for (Map<String, Object> point : memberTrend) {
            groupMemberTrendModel.addRow(new Object[] {
                SimpleJson.asString(point.get("x")),
                SimpleJson.asString(point.get("y"))
            });
        }

        List<Map<String, Object>> recent = SimpleJson.asObjectList(obj.get("recent_activity"));
        for (Map<String, Object> row : recent) {
            groupRecentModel.addRow(new Object[] {
                SimpleJson.asString(row.get("timestamp")),
                SimpleJson.asString(row.get("event")),
                SimpleJson.asString(row.get("actor"))
            });
        }
    }

    private boolean hasError(Map<String, Object> obj) {
        return obj != null && obj.containsKey("error");
    }

    private void addRow(DefaultTableModel model, String key, String value) {
        model.addRow(new Object[] { key, value == null ? "" : value });
    }

    private static class StripeRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
        ) {
            java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                c.setBackground(new Color(52, 93, 120));
                c.setForeground(Color.WHITE);
                return c;
            }
            c.setForeground(INK);
            c.setBackground(row % 2 == 0 ? TABLE_BG : TABLE_ALT);
            return c;
        }
    }

    private static class SimpleJson {
        static Object parse(String input) {
            if (input == null) {
                return Map.of();
            }
            Parser parser = new Parser(input);
            return parser.parseValue();
        }

        static Map<String, Object> asObject(Object value) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                return map;
            }
            return Map.of();
        }

        static List<Map<String, Object>> asObjectList(Object value) {
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object item : list) {
                    out.add(asObject(item));
                }
                return out;
            }
            return List.of();
        }

        static String asString(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof String) {
                return (String) value;
            }
            return String.valueOf(value);
        }

        static String joinArray(Object value) {
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(asString(list.get(i)));
                }
                return sb.toString();
            }
            return "";
        }

        private static class Parser {
            private final String src;
            private int idx;

            Parser(String src) {
                this.src = src.trim();
            }

            Object parseValue() {
                skipWhitespace();
                if (idx >= src.length()) {
                    return Map.of();
                }
                char c = src.charAt(idx);
                if (c == '{') {
                    return parseObject();
                }
                if (c == '[') {
                    return parseArray();
                }
                if (c == '"') {
                    return parseString();
                }
                return parseLiteral();
            }

            private Map<String, Object> parseObject() {
                idx++; // {
                skipWhitespace();
                java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
                if (peek('}')) {
                    idx++;
                    return map;
                }
                while (idx < src.length()) {
                    skipWhitespace();
                    String key = parseString();
                    skipWhitespace();
                    consume(':');
                    Object value = parseValue();
                    map.put(key, value);
                    skipWhitespace();
                    if (peek(',')) {
                        idx++;
                        continue;
                    }
                    if (peek('}')) {
                        idx++;
                        break;
                    }
                }
                return map;
            }

            private List<Object> parseArray() {
                idx++; // [
                skipWhitespace();
                List<Object> list = new ArrayList<>();
                if (peek(']')) {
                    idx++;
                    return list;
                }
                while (idx < src.length()) {
                    Object value = parseValue();
                    list.add(value);
                    skipWhitespace();
                    if (peek(',')) {
                        idx++;
                        continue;
                    }
                    if (peek(']')) {
                        idx++;
                        break;
                    }
                }
                return list;
            }

            private String parseString() {
                consume('"');
                StringBuilder sb = new StringBuilder();
                while (idx < src.length()) {
                    char c = src.charAt(idx++);
                    if (c == '"') {
                        break;
                    }
                    if (c == '\\' && idx < src.length()) {
                        char next = src.charAt(idx++);
                        switch (next) {
                            case '"':
                            case '\\':
                            case '/':
                                sb.append(next);
                                break;
                            case 'b':
                                sb.append('\b');
                                break;
                            case 'f':
                                sb.append('\f');
                                break;
                            case 'n':
                                sb.append('\n');
                                break;
                            case 'r':
                                sb.append('\r');
                                break;
                            case 't':
                                sb.append('\t');
                                break;
                            case 'u':
                                if (idx + 3 < src.length()) {
                                    String hex = src.substring(idx, idx + 4);
                                    idx += 4;
                                    try {
                                        sb.append((char) Integer.parseInt(hex, 16));
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                                break;
                            default:
                                sb.append(next);
                        }
                    } else {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }

            private Object parseLiteral() {
                int start = idx;
                while (idx < src.length()) {
                    char c = src.charAt(idx);
                    if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                        break;
                    }
                    idx++;
                }
                String token = src.substring(start, idx).trim();
                if ("true".equalsIgnoreCase(token)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(token)) {
                    return Boolean.FALSE;
                }
                if ("null".equalsIgnoreCase(token)) {
                    return null;
                }
                try {
                    if (token.contains(".") || token.contains("e") || token.contains("E")) {
                        return Double.parseDouble(token);
                    }
                    return Long.parseLong(token);
                } catch (NumberFormatException e) {
                    return token;
                }
            }

            private void skipWhitespace() {
                while (idx < src.length() && Character.isWhitespace(src.charAt(idx))) {
                    idx++;
                }
            }

            private boolean peek(char c) {
                return idx < src.length() && src.charAt(idx) == c;
            }

            private void consume(char c) {
                if (idx < src.length() && src.charAt(idx) == c) {
                    idx++;
                    return;
                }
                // Best-effort: if format is unexpected, advance to avoid infinite loop.
                idx = Math.min(idx + 1, src.length());
            }
        }
    }
}
