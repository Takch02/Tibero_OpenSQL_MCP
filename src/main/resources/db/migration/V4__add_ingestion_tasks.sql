-- ingestion_log는 감사 이력을 보존한다. 워커가 점유·완료 상태를 바꿀 대상은 버전당 하나의 outbox 작업으로 분리한다.
CREATE TABLE ingestion_tasks (
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT      NOT NULL REFERENCES documents (id),
    document_version INTEGER     NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP   NOT NULL DEFAULT now(),
    started_at       TIMESTAMP,
    UNIQUE (document_id, document_version)
);

-- V4 이전에 생성됐지만 아직 처리되지 않은 이벤트는 작업으로 승격한다.
INSERT INTO ingestion_tasks (document_id, document_version, status, created_at)
SELECT document_id, document_version, 'PENDING', created_at
FROM ingestion_log
WHERE event IN ('CREATED', 'UPDATED', 'RESTORED')
  AND status = 'PENDING'
ON CONFLICT (document_id, document_version) DO NOTHING;

CREATE INDEX idx_ingestion_tasks_pending ON ingestion_tasks (status, id);
