INSERT INTO users(id,telegram_user_id,first_name,created_at,updated_at)
SELECT md5('load-user-'||i)::uuid,20000+i,'Load '||i,now(),now() FROM generate_series(1,500) i ON CONFLICT DO NOTHING;
INSERT INTO community_memberships(community_id,user_id,role,joined_at,telegram_updated_at)
SELECT '11111111-1111-1111-1111-111111111111',md5('load-user-'||i)::uuid,'MEMBER',now(),now() FROM generate_series(1,500) i ON CONFLICT DO NOTHING;
INSERT INTO events(id,community_id,organizer_id,title,scheduling_mode,start_at,end_at,timezone,capacity,status,created_at,updated_at)
SELECT md5('load-event-'||i)::uuid,'11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-000000000001','Load event '||i,'FIXED',now()+interval '1 day',now()+interval '1 day 2 hours','Asia/Tokyo',500,'OPEN',now(),now() FROM generate_series(1,25) i ON CONFLICT DO NOTHING;
INSERT INTO events(id,community_id,organizer_id,title,scheduling_mode,flex_window_start,flex_window_end,duration_minutes,timezone,status,created_at,updated_at)
VALUES(md5('load-flex')::uuid,'11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-000000000001','Load flexible','FLEXIBLE',date_trunc('hour',now()+interval '1 day'),date_trunc('hour',now()+interval '8 days'),120,'Asia/Tokyo','OPEN',now(),now()) ON CONFLICT DO NOTHING;
INSERT INTO event_rsvps(event_id,user_id,status,created_at,updated_at)
SELECT md5('load-flex')::uuid,md5('load-user-'||i)::uuid,'GOING',now(),now() FROM generate_series(1,500) i ON CONFLICT DO NOTHING;
INSERT INTO availability_intervals(id,event_id,user_id,start_at,end_at,created_at)
SELECT md5('load-availability-'||i)::uuid,e.id,md5('load-user-'||i)::uuid,e.flex_window_start,e.flex_window_end,now() FROM generate_series(1,500) i CROSS JOIN events e WHERE e.id=md5('load-flex')::uuid ON CONFLICT DO NOTHING;
