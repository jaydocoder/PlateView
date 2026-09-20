CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE wechat_messages
    ADD COLUMN normalized_content TEXT NOT NULL DEFAULT '',
    ADD COLUMN business_type VARCHAR(32) NOT NULL DEFAULT 'GENERAL_MESSAGE',
    ADD COLUMN catalog_revision BIGINT NOT NULL DEFAULT 0;

UPDATE wechat_messages
SET normalized_content = UPPER(REGEXP_REPLACE(raw_content, '[[:space:]，。；：、,.;:()（）【】\[\]_-]+', '', 'g'));

UPDATE wechat_messages AS m
SET business_type = CASE
    WHEN r.order_number IS NOT NULL AND (m.raw_content LIKE '%【车号】%' OR m.raw_content LIKE '%【时间】%' OR r.raw_plate IS NOT NULL)
        THEN 'STRUCTURED_WORK_ORDER'
    ELSE 'ATTACHMENT_WORK_ORDER'
END
FROM work_order_records AS r
WHERE r.message_id = m.id;

CREATE TABLE wechat_passage_senders (
    sender_username VARCHAR(255) PRIMARY KEY,
    original_display_name VARCHAR(255),
    display_alias VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO wechat_passage_senders(sender_username, original_display_name, display_alias, enabled)
VALUES ('wxid_b0rmsm0lwqjk22', '孙阿鑫', '孙主任', TRUE)
ON CONFLICT(sender_username) DO NOTHING;

UPDATE wechat_messages
SET business_type = 'PASSAGE_MESSAGE'
WHERE sender_username = 'wxid_b0rmsm0lwqjk22'
  AND raw_content LIKE '%通行%'
  AND business_type = 'GENERAL_MESSAGE';

CREATE TABLE work_order_vehicles (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL REFERENCES work_order_records(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    raw_description TEXT NOT NULL,
    raw_plate TEXT NOT NULL,
    normalized_plate TEXT NOT NULL,
    vehicle_type VARCHAR(255),
    UNIQUE(record_id, sequence_number)
);

INSERT INTO work_order_vehicles(record_id, sequence_number, raw_description, raw_plate, normalized_plate, vehicle_type)
SELECT id, 0, COALESCE(vehicle_type || ' ', '') || raw_plate, raw_plate, normalized_plate, vehicle_type
FROM work_order_records
WHERE raw_plate IS NOT NULL;

ALTER TABLE work_order_images
    ADD COLUMN attachment_kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE',
    ADD COLUMN file_name TEXT,
    ADD COLUMN page_count INTEGER,
    ADD COLUMN linked_message_id BIGINT REFERENCES wechat_messages(id) ON DELETE SET NULL,
    ADD COLUMN preview_manifest JSONB NOT NULL DEFAULT '[]'::JSONB;

CREATE INDEX idx_wechat_messages_normalized_trgm
    ON wechat_messages USING GIN(normalized_content gin_trgm_ops);
CREATE INDEX idx_wechat_messages_sender_display_trgm
    ON wechat_messages USING GIN(sender_display gin_trgm_ops);
CREATE INDEX idx_wechat_sources_display_name_trgm
    ON wechat_sources USING GIN(display_name gin_trgm_ops);
CREATE INDEX idx_wechat_messages_business_sent
    ON wechat_messages(business_type, sent_at DESC, id DESC);
CREATE INDEX idx_work_order_vehicles_plate
    ON work_order_vehicles(normalized_plate);
CREATE INDEX idx_work_order_attachments_message
    ON work_order_images(linked_message_id, sent_at, id);

ALTER TABLE wechat_messages
    ADD CONSTRAINT ck_wechat_messages_business_type CHECK (
        business_type IN ('STRUCTURED_WORK_ORDER', 'ATTACHMENT_WORK_ORDER', 'PASSAGE_MESSAGE', 'GENERAL_MESSAGE')
    );
ALTER TABLE work_order_images
    ADD CONSTRAINT ck_work_order_attachment_kind CHECK (attachment_kind IN ('IMAGE', 'PDF', 'FILE'));
