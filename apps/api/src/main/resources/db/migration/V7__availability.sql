CREATE TABLE availability_intervals (
 id UUID PRIMARY KEY,event_id UUID NOT NULL REFERENCES events(id),user_id UUID NOT NULL REFERENCES users(id),
 start_at TIMESTAMPTZ NOT NULL,end_at TIMESTAMPTZ NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 CHECK(end_at>start_at)
);
CREATE INDEX idx_availability_event_user ON availability_intervals(event_id,user_id);
