package server;

import java.util.List;

public class AnalyticsManager {
    private final Database database;

    public AnalyticsManager(Database database) {
        this.database = database;
    }

    public void logUserRegistration(String actorUsername, boolean success, String reasonCode) {
        if (database == null) {
            return;
        }
        database.logAnalyticsEvent(
            "USER_REGISTERED",
            actorUsername,
            "USER",
            actorUsername,
            null,
            success,
            reasonCode
        );
    }

    public void logDmSent(String actorUsername, String targetUsername, String roomKey, boolean success, String reasonCode) {
        if (database == null) {
            return;
        }
        database.logAnalyticsEvent(
            "DM_SENT",
            actorUsername,
            "USER",
            targetUsername,
            roomKey,
            success,
            reasonCode
        );
    }

    public void logGroupEvent(String eventType, String actorUsername, String groupId, boolean success, String reasonCode) {
        if (database == null) {
            return;
        }
        database.logAnalyticsEvent(
            normalizeEventType(eventType),
            actorUsername,
            "GROUP",
            groupId,
            groupId != null ? "grp_" + groupId : null,
            success,
            reasonCode
        );
    }

    public void logHistoryRequest(String actorUsername, String targetType, String targetId, String roomKey, boolean success, String reasonCode) {
        if (database == null) {
            return;
        }
        database.logAnalyticsEvent(
            "HISTORY_REQUESTED",
            actorUsername,
            normalizeTargetType(targetType),
            targetId,
            roomKey,
            success,
            reasonCode
        );
    }

    public void logCommandRejected(String actorUsername, String targetType, String targetId, String reasonCode) {
        if (database == null) {
            return;
        }
        database.logAnalyticsEvent(
            "COMMAND_REJECTED",
            actorUsername,
            normalizeTargetType(targetType),
            targetId,
            null,
            false,
            reasonCode
        );
    }

    public String getOverview(String requesterUsername, String window) {
        if (database == null) {
            return dbUnavailableJson();
        }
        if (!isAnalyticsAdmin(requesterUsername)) {
            return accessDeniedJson();
        }
        String since = toSinceModifier(window);
        int onlineUsers = database.queryAnalyticsInt(
            "SELECT COUNT(DISTINCT actor_username) FROM analytics_events WHERE actor_username IS NOT NULL AND created_at >= datetime('now', '-10 minutes')"
        );
        int dmCount = countByEventAndRoom("DM_SENT", "dm_%", since);
        int groupCount = countByEventAndRoom("GROUP_MESSAGE_SENT", "grp_%", since);
        int totalMessages = dmCount + groupCount;
        int activeGroups = database.queryAnalyticsInt(
            "SELECT COUNT(DISTINCT target_id) FROM analytics_events WHERE event_type = 'GROUP_MESSAGE_SENT' AND success = 1 AND created_at >= datetime('now', ?)",
            since
        );

        int minutes = Math.max(1, windowToMinutes(window));
        double messagesPerMinute = (double) totalMessages / (double) minutes;

        return "{"
            + jsonPair("window", safe(window)) + ","
            + jsonPair("online_users", onlineUsers) + ","
            + jsonPair("messages_per_minute", round2(messagesPerMinute)) + ","
            + jsonPair("messages_total", totalMessages) + ","
            + jsonPair("messages_dm", dmCount) + ","
            + jsonPair("messages_group", groupCount) + ","
            + jsonPair("active_groups", activeGroups)
            + "}";
    }

    public String getSeries(String requesterUsername, String metric, String window, String bucket) {
        if (database == null) {
            return dbUnavailableJson();
        }
        if (!isAnalyticsAdmin(requesterUsername)) {
            return accessDeniedJson();
        }
        String since = toSinceModifier(window);
        String normalizedBucket = normalizeBucket(bucket);
        String bucketExpr = bucketExpression(normalizedBucket);
        String normalizedMetric = safe(metric).toLowerCase();

        String sql;
        Object[] params;
        if ("messages_dm".equals(normalizedMetric)) {
            sql = "SELECT " + bucketExpr + ", COUNT(*) FROM analytics_events "
                + "WHERE event_type = 'DM_SENT' AND success = 1 AND created_at >= datetime('now', ?) "
                + "GROUP BY 1 ORDER BY 1";
            params = new Object[] { since };
        } else if ("messages_group".equals(normalizedMetric)) {
            sql = "SELECT " + bucketExpr + ", COUNT(*) FROM analytics_events "
                + "WHERE event_type = 'GROUP_MESSAGE_SENT' AND success = 1 AND created_at >= datetime('now', ?) "
                + "GROUP BY 1 ORDER BY 1";
            params = new Object[] { since };
        } else if ("registrations".equals(normalizedMetric)) {
            sql = "SELECT " + bucketExpr + ", COUNT(*) FROM analytics_events "
                + "WHERE event_type = 'USER_REGISTERED' AND success = 1 AND created_at >= datetime('now', ?) "
                + "GROUP BY 1 ORDER BY 1";
            params = new Object[] { since };
        } else {
            sql = "SELECT " + bucketExpr + ", COUNT(*) FROM analytics_events "
                + "WHERE event_type IN ('DM_SENT', 'GROUP_MESSAGE_SENT') AND success = 1 AND created_at >= datetime('now', ?) "
                + "GROUP BY 1 ORDER BY 1";
            params = new Object[] { since };
            normalizedMetric = "messages_total";
        }

        List<String[]> rows = database.queryAnalyticsRows(sql, params);

        return "{"
            + jsonPair("metric", normalizedMetric) + ","
            + jsonPair("window", safe(window)) + ","
            + jsonPair("bucket", normalizedBucket) + ","
            + "\"points\":" + pointsJson(rows)
            + "}";
    }

    public String getEngagement(String requesterUsername, String window) {
        if (database == null) {
            return dbUnavailableJson();
        }
        if (!isAnalyticsAdmin(requesterUsername)) {
            return accessDeniedJson();
        }
        String since = toSinceModifier(window);

        int dau = database.queryAnalyticsInt(
            "SELECT COUNT(DISTINCT actor_username) FROM analytics_events WHERE actor_username IS NOT NULL AND success = 1 AND created_at >= datetime('now', '-1 day')"
        );
        int wau = database.queryAnalyticsInt(
            "SELECT COUNT(DISTINCT actor_username) FROM analytics_events WHERE actor_username IS NOT NULL AND success = 1 AND created_at >= datetime('now', '-7 days')"
        );
        int registrations = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type = 'USER_REGISTERED' AND success = 1 AND created_at >= datetime('now', ?)",
            since
        );
        List<String[]> trend = database.queryAnalyticsRows(
            "SELECT strftime('%Y-%m-%d', created_at), COUNT(*) FROM analytics_events "
                + "WHERE event_type = 'USER_REGISTERED' AND success = 1 AND created_at >= datetime('now', ?) "
                + "GROUP BY 1 ORDER BY 1",
            since
        );

        return "{"
            + jsonPair("window", safe(window)) + ","
            + jsonPair("dau", dau) + ","
            + jsonPair("wau", wau) + ","
            + jsonPair("new_registrations", registrations) + ","
            + "\"registration_trend\":" + pointsJson(trend)
            + "}";
    }

    public String getGroupHealth(String requesterUsername, String window) {
        if (database == null) {
            return dbUnavailableJson();
        }
        if (!isAnalyticsAdmin(requesterUsername)) {
            return accessDeniedJson();
        }
        String since = toSinceModifier(window);

        int totalGroups = database.queryAnalyticsInt("SELECT COUNT(*) FROM groups");
        int newGroups = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type = 'GROUP_CREATED' AND success = 1 AND created_at >= datetime('now', ?)",
            since
        );
        int memberAdds = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type = 'GROUP_MEMBER_ADDED' AND success = 1 AND created_at >= datetime('now', ?)",
            since
        );
        int memberRemoves = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type IN ('GROUP_MEMBER_REMOVED', 'GROUP_LEFT') AND success = 1 AND created_at >= datetime('now', ?)",
            since
        );
        int totalMemberships = database.queryAnalyticsInt("SELECT COUNT(*) FROM group_members");
        int ownerMemberships = database.queryAnalyticsInt("SELECT COUNT(*) FROM group_members WHERE role = 'owner'");
        double ownerMemberRatio = totalMemberships == 0 ? 0.0 : (double) ownerMemberships / (double) totalMemberships;
        int inactiveGroups = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM groups g WHERE NOT EXISTS ("
                + "SELECT 1 FROM analytics_events e "
                + "WHERE e.event_type = 'GROUP_MESSAGE_SENT' AND e.target_id = CAST(g.id AS TEXT) "
                + "AND e.created_at >= datetime('now', ?))",
            since
        );

        List<String[]> growthTrend = database.queryAnalyticsRows(
            "SELECT strftime('%Y-%m-%d', created_at), "
                + "SUM(CASE WHEN event_type = 'GROUP_MEMBER_ADDED' THEN 1 WHEN event_type IN ('GROUP_MEMBER_REMOVED', 'GROUP_LEFT') THEN -1 ELSE 0 END) "
                + "FROM analytics_events "
                + "WHERE event_type IN ('GROUP_MEMBER_ADDED', 'GROUP_MEMBER_REMOVED', 'GROUP_LEFT') "
                + "AND success = 1 AND created_at >= datetime('now', ?) "
                + "GROUP BY 1 ORDER BY 1",
            since
        );

        return "{"
            + jsonPair("window", safe(window)) + ","
            + jsonPair("total_groups", totalGroups) + ","
            + jsonPair("new_groups", newGroups) + ","
            + jsonPair("membership_growth", memberAdds - memberRemoves) + ","
            + jsonPair("owner_member_ratio", round2(ownerMemberRatio)) + ","
            + jsonPair("inactive_groups", inactiveGroups) + ","
            + "\"membership_growth_trend\":" + pointsJson(growthTrend)
            + "}";
    }

    public String getUserDrilldown(String requesterUsername, String targetUsername, String window) {
        if (database == null) {
            return dbUnavailableJson();
        }
        if (!isAnalyticsAdmin(requesterUsername)) {
            return accessDeniedJson();
        }
        String since = toSinceModifier(window);
        int dmCount = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type = 'DM_SENT' AND actor_username = ? AND success = 1 AND created_at >= datetime('now', ?)",
            targetUsername,
            since
        );
        int groupCount = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type = 'GROUP_MESSAGE_SENT' AND actor_username = ? AND success = 1 AND created_at >= datetime('now', ?)",
            targetUsername,
            since
        );
        List<String> groups = database.listGroupsForUser(targetUsername);
        List<String[]> trend = database.queryAnalyticsRows(
            "SELECT strftime('%Y-%m-%d', created_at), COUNT(*) FROM analytics_events "
                + "WHERE event_type IN ('DM_SENT', 'GROUP_MESSAGE_SENT') AND actor_username = ? "
                + "AND success = 1 AND created_at >= datetime('now', ?) "
                + "GROUP BY 1 ORDER BY 1",
            targetUsername,
            since
        );

        return "{"
            + jsonPair("window", safe(window)) + ","
            + jsonPair("username", safe(targetUsername)) + ","
            + jsonPair("dm_messages", dmCount) + ","
            + jsonPair("group_messages", groupCount) + ","
            + "\"groups\":" + stringArrayJson(groups) + ","
            + "\"message_trend\":" + pointsJson(trend)
            + "}";
    }

    public String getGroupDrilldown(String requesterUsername, int groupId, String window) {
        if (database == null) {
            return dbUnavailableJson();
        }
        if (!isAnalyticsAdmin(requesterUsername)) {
            return accessDeniedJson();
        }
        String since = toSinceModifier(window);
        String groupIdText = String.valueOf(groupId);
        int memberCount = database.listGroupMembers(groupId).size();
        int messageCount = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type = 'GROUP_MESSAGE_SENT' AND target_id = ? AND success = 1 AND created_at >= datetime('now', ?)",
            groupIdText,
            since
        );
        int ownerChanges = database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type = 'GROUP_OWNER_PROMOTED' AND target_id = ? AND success = 1 AND created_at >= datetime('now', ?)",
            groupIdText,
            since
        );
        List<String[]> messageTrend = database.queryAnalyticsRows(
            "SELECT strftime('%Y-%m-%d', created_at), COUNT(*) FROM analytics_events "
                + "WHERE event_type = 'GROUP_MESSAGE_SENT' AND target_id = ? AND success = 1 "
                + "AND created_at >= datetime('now', ?) GROUP BY 1 ORDER BY 1",
            groupIdText,
            since
        );
        List<String[]> memberTimeline = database.queryAnalyticsRows(
            "SELECT strftime('%Y-%m-%d', created_at), "
                + "SUM(CASE WHEN event_type = 'GROUP_MEMBER_ADDED' THEN 1 WHEN event_type IN ('GROUP_MEMBER_REMOVED', 'GROUP_LEFT') THEN -1 ELSE 0 END) "
                + "FROM analytics_events WHERE event_type IN ('GROUP_MEMBER_ADDED', 'GROUP_MEMBER_REMOVED', 'GROUP_LEFT') "
                + "AND target_id = ? AND success = 1 AND created_at >= datetime('now', ?) "
                + "GROUP BY 1 ORDER BY 1",
            groupIdText,
            since
        );
        List<String[]> recent = database.queryAnalyticsRows(
            "SELECT created_at, event_type, COALESCE(actor_username, '') FROM analytics_events "
                + "WHERE target_type = 'GROUP' AND target_id = ? ORDER BY created_at DESC LIMIT 20",
            groupIdText
        );

        return "{"
            + jsonPair("window", safe(window)) + ","
            + jsonPair("group_id", groupId) + ","
            + jsonPair("member_count", memberCount) + ","
            + jsonPair("message_count", messageCount) + ","
            + jsonPair("owner_changes", ownerChanges) + ","
            + "\"message_trend\":" + pointsJson(messageTrend) + ","
            + "\"member_timeline\":" + pointsJson(memberTimeline) + ","
            + "\"recent_activity\":" + recentActivityJson(recent)
            + "}";
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.trim().isEmpty()) {
            return "GROUP_MESSAGE_SENT";
        }
        return eventType.trim().toUpperCase();
    }

    private String normalizeTargetType(String targetType) {
        if (targetType == null || targetType.trim().isEmpty()) {
            return "SYSTEM";
        }
        String normalized = targetType.trim().toUpperCase();
        if ("USER".equals(normalized) || "GROUP".equals(normalized) || "SYSTEM".equals(normalized)) {
            return normalized;
        }
        return "SYSTEM";
    }

    private boolean isAnalyticsAdmin(String username) {
        return username != null && "admin".equalsIgnoreCase(username.trim());
    }

    private String accessDeniedJson() {
        return "{\"error\":\"ACCESS_DENIED\",\"message\":\"Analytics access denied\"}";
    }

    private String dbUnavailableJson() {
        return "{\"error\":\"DB_UNAVAILABLE\",\"message\":\"Analytics database unavailable\"}";
    }

    private String toSinceModifier(String window) {
        if (window == null) {
            return "-24 hours";
        }
        String w = window.trim().toLowerCase();
        if ("24h".equals(w)) {
            return "-24 hours";
        }
        if ("7d".equals(w)) {
            return "-7 days";
        }
        if ("30d".equals(w)) {
            return "-30 days";
        }
        if (w.endsWith("h")) {
            return "-" + safeInt(w.substring(0, w.length() - 1), 24) + " hours";
        }
        if (w.endsWith("d")) {
            return "-" + safeInt(w.substring(0, w.length() - 1), 7) + " days";
        }
        return "-24 hours";
    }

    private int windowToMinutes(String window) {
        if (window == null) {
            return 24 * 60;
        }
        String w = window.trim().toLowerCase();
        if ("24h".equals(w)) {
            return 24 * 60;
        }
        if ("7d".equals(w)) {
            return 7 * 24 * 60;
        }
        if ("30d".equals(w)) {
            return 30 * 24 * 60;
        }
        if (w.endsWith("h")) {
            return safeInt(w.substring(0, w.length() - 1), 24) * 60;
        }
        if (w.endsWith("d")) {
            return safeInt(w.substring(0, w.length() - 1), 7) * 24 * 60;
        }
        return 24 * 60;
    }

    private String normalizeBucket(String bucket) {
        if (bucket == null) {
            return "hour";
        }
        String b = bucket.trim().toLowerCase();
        if ("minute".equals(b) || "hour".equals(b) || "day".equals(b)) {
            return b;
        }
        return "hour";
    }

    private String bucketExpression(String bucket) {
        if ("minute".equals(bucket)) {
            return "strftime('%Y-%m-%d %H:%M', created_at)";
        }
        if ("day".equals(bucket)) {
            return "strftime('%Y-%m-%d', created_at)";
        }
        return "strftime('%Y-%m-%d %H:00', created_at)";
    }

    private int countByEventAndRoom(String eventType, String roomLike, String sinceModifier) {
        return database.queryAnalyticsInt(
            "SELECT COUNT(*) FROM analytics_events WHERE event_type = ? AND success = 1 AND room_key LIKE ? AND created_at >= datetime('now', ?)",
            eventType,
            roomLike,
            sinceModifier
        );
    }

    private int safeInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String jsonPair(String key, String value) {
        return "\"" + safe(key) + "\":\"" + safe(value) + "\"";
    }

    private String jsonPair(String key, int value) {
        return "\"" + safe(key) + "\":" + value;
    }

    private String jsonPair(String key, double value) {
        return "\"" + safe(key) + "\":" + value;
    }

    private String pointsJson(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (i > 0) {
                sb.append(",");
            }
            String x = row.length > 0 ? row[0] : "";
            String y = row.length > 1 ? row[1] : "0";
            sb.append("{").append(jsonPair("x", x)).append(",").append(jsonPair("y", safeInt(y, 0))).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String stringArrayJson(List<String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(safe(values.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String recentActivityJson(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (i > 0) {
                sb.append(",");
            }
            String ts = row.length > 0 ? row[0] : "";
            String event = row.length > 1 ? row[1] : "";
            String actor = row.length > 2 ? row[2] : "";
            sb.append("{")
                .append(jsonPair("timestamp", ts)).append(",")
                .append(jsonPair("event", event)).append(",")
                .append(jsonPair("actor", actor))
                .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
