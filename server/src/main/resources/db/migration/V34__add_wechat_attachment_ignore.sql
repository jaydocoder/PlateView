ALTER TABLE work_order_images ADD COLUMN ignored_at TIMESTAMPTZ;
CREATE INDEX idx_work_order_images_pending ON work_order_images(availability, ignored_at, linked_message_id, sent_at);
