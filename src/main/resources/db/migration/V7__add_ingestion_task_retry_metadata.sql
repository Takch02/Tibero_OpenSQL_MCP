ALTER TABLE ingestion_tasks
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP NOT NULL DEFAULT now(),
    ADD COLUMN last_error TEXT;

CREATE INDEX idx_ingestion_tasks_pending_due
    ON ingestion_tasks (status, next_attempt_at, id);
