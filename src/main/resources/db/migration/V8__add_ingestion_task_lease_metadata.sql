ALTER TABLE ingestion_tasks
    -- PROCESSING 작업의 현재 소유자와 heartbeat를 저장해 비정상 종료 뒤 안전하게 회수한다.
    ADD COLUMN claimed_by VARCHAR(255),
    ADD COLUMN heartbeat_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_ingestion_tasks_processing_lease
    ON ingestion_tasks (status, lease_expires_at, id);
