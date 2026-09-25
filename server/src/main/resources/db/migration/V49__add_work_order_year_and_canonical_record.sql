ALTER TABLE wechat_messages
    ADD COLUMN referenced_order_number VARCHAR(128),
    ADD COLUMN referenced_order_year INTEGER;

ALTER TABLE work_order_records
    ADD COLUMN order_year INTEGER,
    ADD COLUMN is_canonical BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE wechat_messages m
SET referenced_order_number = r.order_number
FROM work_order_records r
WHERE r.message_id = m.id AND r.order_number IS NOT NULL;

UPDATE wechat_messages
SET referenced_order_number = (regexp_match(
        raw_content,
        '(^|[^0-9])((0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3})($|[^0-9])'
    ))[2]
WHERE referenced_order_number IS NULL;

UPDATE wechat_messages
SET referenced_order_year = EXTRACT(YEAR FROM sent_at AT TIME ZONE 'Asia/Shanghai')::INTEGER
WHERE referenced_order_number IS NOT NULL;

UPDATE work_order_records r
SET order_year = m.referenced_order_year
FROM wechat_messages m
WHERE m.id = r.message_id;

WITH ranked AS (
    SELECT r.id,
           ROW_NUMBER() OVER (
               PARTITION BY m.source_id, r.order_year, r.order_number
               ORDER BY
                   CASE WHEN r.status = 'VOID' THEN 0 ELSE 1 END,
                   CASE WHEN m.business_type = 'STRUCTURED_WORK_ORDER' THEN 0 ELSE 1 END,
                   CASE r.parse_quality WHEN 'COMPLETE' THEN 0 WHEN 'PARTIAL' THEN 1 ELSE 2 END,
                   ((r.raw_plate IS NOT NULL)::INTEGER + (r.raw_valid_time IS NOT NULL)::INTEGER +
                    (r.location IS NOT NULL)::INTEGER + (r.reason IS NOT NULL)::INTEGER +
                    (r.remarks IS NOT NULL)::INTEGER) DESC,
                   m.sent_at DESC,
                   r.id DESC
           ) AS position
    FROM work_order_records r
    JOIN wechat_messages m ON m.id = r.message_id
    WHERE r.order_number IS NOT NULL AND r.order_year IS NOT NULL
)
UPDATE work_order_records r
SET is_canonical = ranked.position = 1
FROM ranked
WHERE ranked.id = r.id;

UPDATE work_order_records
SET is_canonical = TRUE
WHERE order_number IS NULL;

WITH attachment_targets AS (
    SELECT image.id AS image_id, canonical.id AS canonical_id
    FROM work_order_images image
    JOIN work_order_records current_record ON current_record.id = image.linked_record_id
    JOIN wechat_messages current_message ON current_message.id = current_record.message_id
    JOIN work_order_records canonical
      ON canonical.order_year = current_record.order_year
     AND canonical.order_number = current_record.order_number
     AND canonical.is_canonical
    JOIN wechat_messages canonical_message
      ON canonical_message.id = canonical.message_id
     AND canonical_message.source_id = current_message.source_id
    WHERE image.linked_record_id <> canonical.id
)
UPDATE work_order_images image
SET linked_record_id = attachment_targets.canonical_id
FROM attachment_targets
WHERE image.id = attachment_targets.image_id;

WITH supporting_targets AS (
    SELECT image.id AS image_id,
           (
               SELECT MIN(message.id)
               FROM wechat_messages message
               JOIN work_order_records short_record ON short_record.message_id = message.id
               WHERE message.source_id = canonical_message.source_id
                 AND short_record.order_year = canonical.order_year
                 AND short_record.order_number = canonical.order_number
                 AND message.business_type = 'ATTACHMENT_WORK_ORDER'
                 AND NOT short_record.is_canonical
               HAVING COUNT(*) = 1
           ) AS supporting_message_id
    FROM work_order_images image
    JOIN work_order_records canonical ON canonical.id = image.linked_record_id AND canonical.is_canonical
    JOIN wechat_messages canonical_message ON canonical_message.id = canonical.message_id
)
UPDATE work_order_images image
SET linked_message_id = supporting_targets.supporting_message_id
FROM supporting_targets
WHERE image.id = supporting_targets.image_id
  AND image.linked_message_id IS DISTINCT FROM supporting_targets.supporting_message_id
  AND supporting_targets.supporting_message_id IS NOT NULL;

CREATE INDEX idx_wechat_messages_order_reference
    ON wechat_messages(source_id, referenced_order_year, referenced_order_number, sent_at DESC, id DESC)
    WHERE referenced_order_number IS NOT NULL;

CREATE INDEX idx_work_order_canonical_group
    ON work_order_records(order_year, order_number, is_canonical, id)
    WHERE order_number IS NOT NULL;

CREATE OR REPLACE FUNCTION select_replacement_work_order_before_delete()
RETURNS TRIGGER AS $$
DECLARE
    group_source_id BIGINT;
    replacement_record_id BIGINT;
    supporting_message_id BIGINT;
    replacement_revision BIGINT;
BEGIN
    IF NOT OLD.is_canonical OR OLD.order_number IS NULL OR OLD.order_year IS NULL THEN
        RETURN OLD;
    END IF;

    SELECT source_id INTO group_source_id
    FROM wechat_messages
    WHERE id = OLD.message_id;

    IF group_source_id IS NULL THEN
        RETURN OLD;
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended(
        group_source_id::TEXT || ':' || OLD.order_year::TEXT || ':' || OLD.order_number,
        0
    ));

    SELECT candidate.id INTO replacement_record_id
    FROM work_order_records candidate
    JOIN wechat_messages candidate_message ON candidate_message.id = candidate.message_id
    WHERE candidate.id <> OLD.id
      AND candidate_message.source_id = group_source_id
      AND candidate.order_year = OLD.order_year
      AND candidate.order_number = OLD.order_number
    ORDER BY CASE WHEN candidate.status = 'VOID' THEN 0 ELSE 1 END,
             CASE WHEN candidate_message.business_type = 'STRUCTURED_WORK_ORDER' THEN 0 ELSE 1 END,
             CASE candidate.parse_quality WHEN 'COMPLETE' THEN 0 WHEN 'PARTIAL' THEN 1 ELSE 2 END,
             ((candidate.raw_plate IS NOT NULL)::INTEGER + (candidate.raw_valid_time IS NOT NULL)::INTEGER +
              (candidate.location IS NOT NULL)::INTEGER + (candidate.reason IS NOT NULL)::INTEGER +
              (candidate.remarks IS NOT NULL)::INTEGER) DESC,
             candidate_message.sent_at DESC,
             candidate.id DESC
    LIMIT 1;

    IF replacement_record_id IS NULL THEN
        RETURN OLD;
    END IF;

    UPDATE work_order_catalog_state
    SET revision = revision + 1
    WHERE id = 1
    RETURNING revision INTO replacement_revision;

    UPDATE work_order_records candidate
    SET is_canonical = candidate.id = replacement_record_id,
        catalog_revision = replacement_revision
    FROM wechat_messages candidate_message
    WHERE candidate_message.id = candidate.message_id
      AND candidate.id <> OLD.id
      AND candidate_message.source_id = group_source_id
      AND candidate.order_year = OLD.order_year
      AND candidate.order_number = OLD.order_number;

    UPDATE wechat_messages candidate_message
    SET catalog_revision = replacement_revision
    WHERE candidate_message.id IN (
        SELECT candidate.message_id
        FROM work_order_records candidate
        WHERE candidate.id <> OLD.id
          AND candidate.order_year = OLD.order_year
          AND candidate.order_number = OLD.order_number
    ) AND candidate_message.source_id = group_source_id;

    UPDATE work_order_images
    SET linked_record_id = replacement_record_id
    WHERE linked_record_id = OLD.id;

    SELECT MIN(candidate_message.id) INTO supporting_message_id
    FROM wechat_messages candidate_message
    JOIN work_order_records candidate ON candidate.message_id = candidate_message.id
    WHERE candidate_message.source_id = group_source_id
      AND candidate.order_year = OLD.order_year
      AND candidate.order_number = OLD.order_number
      AND candidate_message.business_type = 'ATTACHMENT_WORK_ORDER'
      AND NOT candidate.is_canonical
    HAVING COUNT(*) = 1;

    IF supporting_message_id IS NOT NULL THEN
        UPDATE work_order_images
        SET linked_message_id = supporting_message_id
        WHERE linked_record_id = replacement_record_id
          AND linked_message_id IS DISTINCT FROM supporting_message_id;
    END IF;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_select_replacement_work_order_before_delete
BEFORE DELETE ON work_order_records
FOR EACH ROW EXECUTE FUNCTION select_replacement_work_order_before_delete();

DO $$
DECLARE
    rebuild_revision BIGINT;
BEGIN
    SELECT GREATEST(
        COALESCE((SELECT revision FROM work_order_catalog_state WHERE id = 1), 0),
        COALESCE((SELECT MAX(catalog_revision) FROM work_order_records), 0),
        COALESCE((SELECT MAX(catalog_revision) FROM wechat_messages), 0),
        COALESCE((SELECT work_order_revision FROM client_catalog_state WHERE id = 1), 0),
        COALESCE((SELECT wechat_message_revision FROM client_catalog_state WHERE id = 1), 0)
    ) + 1 INTO rebuild_revision;

    UPDATE work_order_catalog_state
    SET revision = rebuild_revision
    WHERE id = 1;

    TRUNCATE work_order_catalog_changes, wechat_message_catalog_changes;

    UPDATE work_order_records SET catalog_revision = rebuild_revision;
    UPDATE wechat_messages SET catalog_revision = rebuild_revision;
END;
$$;
