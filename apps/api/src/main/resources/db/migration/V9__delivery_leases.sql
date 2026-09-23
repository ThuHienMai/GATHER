ALTER TABLE notification_deliveries ADD COLUMN lease_token UUID;
CREATE UNIQUE INDEX idx_one_reminder_per_schedule ON outbox_events(aggregate_id,event_type,(payload->>'start_at')) WHERE event_type='EVENT_STARTING_SOON';
