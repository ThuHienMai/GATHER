-- Reset only synthetic fixed-event attendance before comparable load runs.
DELETE FROM event_rsvps WHERE event_id IN (SELECT md5('load-event-'||i)::uuid FROM generate_series(1,25) i);
