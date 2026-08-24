ALTER TABLE vehicle_query_events
    ADD COLUMN event_id UUID;

ALTER TABLE vehicle_query_events
    ADD CONSTRAINT uq_vehicle_query_events_event_id UNIQUE (event_id);

CREATE INDEX idx_vehicle_query_events_queried_at
    ON vehicle_query_events (queried_at DESC);
