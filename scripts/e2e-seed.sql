INSERT INTO users(id,telegram_user_id,first_name,created_at,updated_at) VALUES
 ('00000000-0000-0000-0000-000000000001',1001,'Alice',now(),now()),
 ('00000000-0000-0000-0000-000000000002',1002,'Bob',now(),now()) ON CONFLICT DO NOTHING;
INSERT INTO communities(id,name,slug,telegram_chat_id,timezone,created_at) VALUES
 ('11111111-1111-1111-1111-111111111111','Minerva Tokyo · Test','minerva-test',-1001,'Asia/Tokyo',now()) ON CONFLICT DO NOTHING;
INSERT INTO community_memberships(community_id,user_id,role,joined_at,telegram_updated_at) VALUES
 ('11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-000000000001','ADMIN',now(),now()),
 ('11111111-1111-1111-1111-111111111111','00000000-0000-0000-0000-000000000002','MEMBER',now(),now()) ON CONFLICT DO NOTHING;
