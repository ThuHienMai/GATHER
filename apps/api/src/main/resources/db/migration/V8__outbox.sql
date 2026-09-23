CREATE TABLE outbox_events (
 id UUID PRIMARY KEY,aggregate_type VARCHAR(50) NOT NULL,aggregate_id UUID NOT NULL,
 event_type VARCHAR(80) NOT NULL,payload JSONB NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,processed_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events(occurred_at) WHERE processed_at IS NULL;
CREATE TABLE notification_preferences (
 event_id UUID NOT NULL REFERENCES events(id),user_id UUID NOT NULL REFERENCES users(id),
 level VARCHAR(20) NOT NULL CHECK(level IN ('ALL_ACTIVITY','IMPORTANT_ONLY','MUTED')),
 PRIMARY KEY(event_id,user_id)
);
CREATE TABLE notification_deliveries (
 id UUID PRIMARY KEY,outbox_event_id UUID NOT NULL REFERENCES outbox_events(id),recipient_user_id UUID NOT NULL REFERENCES users(id),
 channel VARCHAR(20) NOT NULL DEFAULT 'TELEGRAM',status VARCHAR(20) NOT NULL,
 attempt_count INTEGER NOT NULL DEFAULT 0,next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),locked_until TIMESTAMPTZ,
 telegram_message_id BIGINT,created_at TIMESTAMPTZ NOT NULL,sent_at TIMESTAMPTZ,
 UNIQUE(outbox_event_id,recipient_user_id,channel),
 CHECK(status IN ('PENDING','IN_PROGRESS','RETRY','SENT','FAILED_FINAL','SKIPPED'))
);
CREATE INDEX idx_delivery_pending ON notification_deliveries(status,next_attempt_at);
