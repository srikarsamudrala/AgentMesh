#!/usr/bin/env bash
set -euo pipefail

DB_PATH="${1:-chat.db}"
MODE="${2:-append}"  # append | reset

if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "Error: sqlite3 is required but not installed." >&2
  exit 1
fi

if [[ ! -f "$DB_PATH" ]]; then
  echo "Error: database not found at '$DB_PATH'" >&2
  exit 1
fi

sqlite3 "$DB_PATH" <<'SQL'
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS analytics_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_type TEXT NOT NULL,
  actor_username TEXT,
  target_type TEXT,
  target_id TEXT,
  room_key TEXT,
  success INTEGER NOT NULL,
  reason_code TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS analytics_timeseries (
  bucket_start DATETIME NOT NULL,
  bucket_granularity TEXT NOT NULL,
  metric_key TEXT NOT NULL,
  chat_type TEXT NOT NULL DEFAULT 'ALL',
  metric_value INTEGER NOT NULL,
  PRIMARY KEY (bucket_start, bucket_granularity, metric_key, chat_type)
);

CREATE TABLE IF NOT EXISTS analytics_daily_engagement (
  summary_date DATE NOT NULL PRIMARY KEY,
  dau INTEGER NOT NULL,
  wau INTEGER NOT NULL,
  new_registrations INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics_daily_group_health (
  summary_date DATE NOT NULL PRIMARY KEY,
  total_groups INTEGER NOT NULL,
  new_groups INTEGER NOT NULL,
  total_memberships INTEGER NOT NULL,
  owner_memberships INTEGER NOT NULL,
  inactive_groups INTEGER NOT NULL
);
SQL

if [[ "$MODE" == "reset" ]]; then
  sqlite3 "$DB_PATH" <<'SQL'
DELETE FROM analytics_events;
DELETE FROM analytics_timeseries;
DELETE FROM analytics_daily_engagement;
DELETE FROM analytics_daily_group_health;
SQL
fi

sqlite3 "$DB_PATH" <<'SQL'
BEGIN TRANSACTION;

-- Recent registrations
INSERT INTO analytics_events (event_type, actor_username, target_type, target_id, room_key, success, reason_code, created_at)
VALUES
('USER_REGISTERED', 'alice', 'USER', 'alice', NULL, 1, NULL, datetime('now', '-6 days')),
('USER_REGISTERED', 'bob', 'USER', 'bob', NULL, 1, NULL, datetime('now', '-5 days')),
('USER_REGISTERED', 'carol', 'USER', 'carol', NULL, 1, NULL, datetime('now', '-3 days')),
('USER_REGISTERED', 'dave', 'USER', 'dave', NULL, 1, NULL, datetime('now', '-1 day'));

-- DM + Group message traffic
INSERT INTO analytics_events (event_type, actor_username, target_type, target_id, room_key, success, reason_code, created_at)
VALUES
('DM_SENT', 'alice', 'USER', 'bob', 'dm_alice_bob', 1, NULL, datetime('now', '-10 hours')),
('DM_SENT', 'bob', 'USER', 'alice', 'dm_alice_bob', 1, NULL, datetime('now', '-9 hours')),
('DM_SENT', 'carol', 'USER', 'dave', 'dm_carol_dave', 1, NULL, datetime('now', '-8 hours')),
('GROUP_MESSAGE_SENT', 'alice', 'GROUP', '1', 'grp_1', 1, NULL, datetime('now', '-7 hours')),
('GROUP_MESSAGE_SENT', 'bob', 'GROUP', '1', 'grp_1', 1, NULL, datetime('now', '-6 hours')),
('GROUP_MESSAGE_SENT', 'dave', 'GROUP', '2', 'grp_2', 1, NULL, datetime('now', '-5 hours')),
('GROUP_MESSAGE_SENT', 'carol', 'GROUP', '2', 'grp_2', 1, NULL, datetime('now', '-4 hours')),
('GROUP_MESSAGE_SENT', 'eve', 'GROUP', '3', 'grp_3', 0, 'not_member', datetime('now', '-2 hours'));

-- Group lifecycle / membership events
INSERT INTO analytics_events (event_type, actor_username, target_type, target_id, room_key, success, reason_code, created_at)
VALUES
('GROUP_CREATED', 'alice', 'GROUP', '1', 'grp_1', 1, NULL, datetime('now', '-7 days')),
('GROUP_CREATED', 'dave', 'GROUP', '2', 'grp_2', 1, NULL, datetime('now', '-2 days')),
('GROUP_MEMBER_ADDED', 'alice', 'GROUP', '1', 'grp_1', 1, NULL, datetime('now', '-6 days')),
('GROUP_MEMBER_ADDED', 'dave', 'GROUP', '2', 'grp_2', 1, NULL, datetime('now', '-2 days')),
('GROUP_OWNER_PROMOTED', 'alice', 'GROUP', '1', 'grp_1', 1, NULL, datetime('now', '-1 day')),
('GROUP_LEFT', 'bob', 'GROUP', '1', 'grp_1', 1, NULL, datetime('now', '-6 hours'));

-- History and rejected command signals
INSERT INTO analytics_events (event_type, actor_username, target_type, target_id, room_key, success, reason_code, created_at)
VALUES
('HISTORY_REQUESTED', 'carol', 'USER', 'dave', 'dm_carol_dave', 1, NULL, datetime('now', '-3 hours')),
('HISTORY_REQUESTED', 'eve', 'GROUP', '1', 'grp_1', 0, 'not_member', datetime('now', '-2 hours')),
('COMMAND_REJECTED', 'eve', 'GROUP', '1', NULL, 0, 'group_message_not_member', datetime('now', '-2 hours')),
('COMMAND_REJECTED', 'mallory', 'SYSTEM', '/analytics_overview', NULL, 0, 'unknown_command', datetime('now', '-30 minutes'));

-- Optional mock rollups (for faster dashboard reads)
INSERT OR REPLACE INTO analytics_daily_engagement (summary_date, dau, wau, new_registrations)
VALUES
(date('now', '-2 day'), 4, 7, 1),
(date('now', '-1 day'), 5, 8, 1),
(date('now'), 5, 9, 0);

INSERT OR REPLACE INTO analytics_daily_group_health
(summary_date, total_groups, new_groups, total_memberships, owner_memberships, inactive_groups)
VALUES
(date('now', '-2 day'), 2, 1, 6, 2, 0),
(date('now', '-1 day'), 3, 1, 8, 3, 1),
(date('now'), 3, 0, 8, 3, 1);

INSERT OR REPLACE INTO analytics_timeseries (bucket_start, bucket_granularity, metric_key, chat_type, metric_value)
VALUES
(datetime('now', '-3 hours'), 'hour', 'messages_total', 'ALL', 3),
(datetime('now', '-2 hours'), 'hour', 'messages_total', 'ALL', 4),
(datetime('now', '-1 hours'), 'hour', 'messages_total', 'ALL', 2),
(datetime('now', '-3 hours'), 'hour', 'messages_total', 'DM', 1),
(datetime('now', '-2 hours'), 'hour', 'messages_total', 'DM', 1),
(datetime('now', '-1 hours'), 'hour', 'messages_total', 'DM', 0),
(datetime('now', '-3 hours'), 'hour', 'messages_total', 'GROUP', 2),
(datetime('now', '-2 hours'), 'hour', 'messages_total', 'GROUP', 3),
(datetime('now', '-1 hours'), 'hour', 'messages_total', 'GROUP', 2);

COMMIT;
SQL

echo "Mock analytics data inserted into: $DB_PATH (mode=$MODE)"
