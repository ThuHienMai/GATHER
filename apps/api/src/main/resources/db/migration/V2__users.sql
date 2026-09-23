CREATE TABLE users (
 id UUID PRIMARY KEY, telegram_user_id BIGINT UNIQUE NOT NULL,
 telegram_username VARCHAR(64), first_name VARCHAR(128), last_name VARCHAR(128),
 timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Tokyo',
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 last_active_at TIMESTAMPTZ, telegram_dm_enabled BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE user_activity_daily (
 user_id UUID NOT NULL REFERENCES users(id), activity_date DATE NOT NULL,
 PRIMARY KEY(user_id, activity_date)
);
