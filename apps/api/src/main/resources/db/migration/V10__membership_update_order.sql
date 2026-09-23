ALTER TABLE community_memberships ADD COLUMN telegram_update_id BIGINT NOT NULL DEFAULT -1;
ALTER TABLE communities ADD COLUMN telegram_updated_at TIMESTAMPTZ NOT NULL DEFAULT '-infinity';
ALTER TABLE communities ADD COLUMN telegram_update_id BIGINT NOT NULL DEFAULT -1;
