CREATE TABLE event_rsvps (
 event_id UUID NOT NULL REFERENCES events(id), user_id UUID NOT NULL REFERENCES users(id),
 status VARCHAR(20) NOT NULL CHECK(status IN ('GOING','MAYBE','WAITLISTED')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, waitlisted_at TIMESTAMPTZ,
 PRIMARY KEY(event_id,user_id), CHECK(status!='WAITLISTED' OR waitlisted_at IS NOT NULL)
);
CREATE INDEX idx_rsvps_event_status ON event_rsvps(event_id,status);
