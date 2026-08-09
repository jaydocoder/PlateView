CREATE INDEX idx_audit_logs_created_at_id_desc
    ON audit_logs (created_at DESC, id DESC);

CREATE INDEX idx_audit_logs_result_created_at_id_desc
    ON audit_logs (result_status, created_at DESC, id DESC);
