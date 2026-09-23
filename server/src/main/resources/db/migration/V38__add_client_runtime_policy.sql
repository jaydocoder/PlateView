CREATE TABLE client_runtime_policy (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    revision BIGINT NOT NULL DEFAULT 1,
    vehicle_result_limit INTEGER NOT NULL DEFAULT 8 CHECK (vehicle_result_limit BETWEEN 0 AND 50),
    work_order_result_limit INTEGER NOT NULL DEFAULT 8 CHECK (work_order_result_limit BETWEEN 0 AND 50),
    wechat_message_result_limit INTEGER NOT NULL DEFAULT 8 CHECK (wechat_message_result_limit BETWEEN 0 AND 50),
    active_api_base_url TEXT NOT NULL DEFAULT 'https://api.chenxiruyu.dpdns.org/',
    previous_api_base_url TEXT,
    active_update_base_url TEXT NOT NULL DEFAULT 'https://api.chenxiruyu.dpdns.org/updates/',
    previous_update_base_url TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES users(id)
);

INSERT INTO client_runtime_policy(id) VALUES (1);

ALTER TABLE users
    ADD COLUMN cache_reset_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN cache_reset_requested_at TIMESTAMPTZ,
    ADD COLUMN cache_reset_requested_by BIGINT REFERENCES users(id);

CREATE TABLE client_instance_state (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_instance_id VARCHAR(64) NOT NULL,
    applied_cache_reset_revision BIGINT NOT NULL DEFAULT 0,
    applied_policy_revision BIGINT NOT NULL DEFAULT 0,
    last_api_base_url TEXT,
    last_update_base_url TEXT,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, client_instance_id)
);

CREATE INDEX idx_client_instance_state_last_seen
    ON client_instance_state(user_id, last_seen_at DESC);
