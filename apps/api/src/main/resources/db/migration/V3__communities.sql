CREATE TABLE communities (
 id UUID PRIMARY KEY, name VARCHAR(150) NOT NULL, slug VARCHAR(80) UNIQUE NOT NULL,
 telegram_chat_id BIGINT UNIQUE NOT NULL, timezone VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, bot_active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE community_memberships (
 community_id UUID NOT NULL REFERENCES communities(id), user_id UUID NOT NULL REFERENCES users(id),
 role VARCHAR(20) NOT NULL CHECK(role IN ('MEMBER','ADMIN')), joined_at TIMESTAMPTZ NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, telegram_updated_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(community_id,user_id)
);
CREATE TABLE telegram_updates (
 update_id BIGINT PRIMARY KEY, received_at TIMESTAMPTZ NOT NULL, processed_at TIMESTAMPTZ
);
