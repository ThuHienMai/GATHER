CREATE TABLE events (
 id UUID PRIMARY KEY, community_id UUID NOT NULL REFERENCES communities(id), organizer_id UUID NOT NULL REFERENCES users(id),
 title VARCHAR(120) NOT NULL CHECK(length(title)>=3), description VARCHAR(2000), location_text VARCHAR(250), location_url VARCHAR(500),
 scheduling_mode VARCHAR(20) NOT NULL CHECK(scheduling_mode IN ('FIXED','FLEXIBLE')),
 start_at TIMESTAMPTZ, end_at TIMESTAMPTZ, flex_window_start TIMESTAMPTZ, flex_window_end TIMESTAMPTZ, duration_minutes INTEGER,
 timezone VARCHAR(64) NOT NULL, capacity INTEGER CHECK(capacity>0),
 status VARCHAR(20) NOT NULL CHECK(status IN ('OPEN','LOCKED','CANCELLED','COMPLETED')),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CHECK((start_at IS NULL AND end_at IS NULL) OR (start_at IS NOT NULL AND end_at>start_at)),
 CHECK(scheduling_mode!='FIXED' OR start_at IS NOT NULL),
 CHECK(scheduling_mode!='FLEXIBLE' OR (flex_window_start IS NOT NULL AND flex_window_end>flex_window_start AND duration_minutes BETWEEN 30 AND 480))
);
CREATE INDEX idx_events_community_start ON events(community_id,start_at);
CREATE INDEX idx_events_community_status ON events(community_id,status);
