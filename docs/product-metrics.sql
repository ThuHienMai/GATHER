-- Run only against the intended production database; exclude synthetic environments.
SELECT count(*) AS registered_users FROM users;
SELECT count(DISTINCT user_id) AS weekly_active_users FROM user_activity_daily WHERE activity_date>=CURRENT_DATE-6;
SELECT count(*) AS events_created FROM events;
SELECT count(*) AS current_rsvps FROM event_rsvps;
SELECT count(DISTINCT (event_id,user_id)) AS current_availability_submitters FROM availability_intervals;
SELECT count(*) AS comments_created FROM comments;
SELECT status,count(*) FROM notification_deliveries GROUP BY status;
-- Current RSVP/availability rows measure current participation, not historical submissions.
-- Outbox RSVP_UPDATED and AVAILABILITY_UPDATED count successful changes after outbox introduction.
SELECT event_type,count(*) FROM outbox_events WHERE event_type IN ('RSVP_UPDATED','AVAILABILITY_UPDATED','EVENT_TIME_CHANGED') GROUP BY event_type;
