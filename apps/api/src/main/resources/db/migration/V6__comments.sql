CREATE TABLE comments (
 id UUID PRIMARY KEY, event_id UUID NOT NULL REFERENCES events(id), author_id UUID NOT NULL REFERENCES users(id),
 section VARCHAR(20) NOT NULL CHECK(section IN ('GENERAL','TIME','LOCATION')),
 parent_comment_id UUID REFERENCES comments(id), body VARCHAR(2000) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, edited_at TIMESTAMPTZ, deleted_at TIMESTAMPTZ,
 CHECK(length(body)>0)
);
CREATE INDEX idx_comments_event_created ON comments(event_id,created_at);
