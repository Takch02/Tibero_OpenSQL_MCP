-- documents는 논리 문서의 현재 메타데이터를 유지하고, 실제 원문 이력은 document_versions에 보관한다.
-- current_search_version은 마지막으로 임베딩까지 정상 완료된 버전이다. 새 버전 처리 중에도 이전 정상
-- 버전을 검색에 노출해, 원문과 벡터의 교체가 원자적으로 보이도록 한다.
ALTER TABLE documents ADD COLUMN current_search_version INTEGER;
ALTER TABLE documents ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE ingestion_log ADD COLUMN document_version INTEGER;

UPDATE documents
SET current_search_version = version
WHERE status = 'EMBEDDED';

UPDATE ingestion_log l
SET document_version = d.version
FROM documents d
WHERE d.id = l.document_id;

ALTER TABLE ingestion_log ALTER COLUMN document_version SET NOT NULL;

CREATE TABLE document_versions (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT       NOT NULL REFERENCES documents (id),
    version      INTEGER      NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    content      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_by   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (document_id, version)
);

-- V3 적용 전에 존재하던 문서도 버전 이력으로 승격한다.
INSERT INTO document_versions (document_id, version, content_hash, content, status, created_by, created_at)
SELECT id, version, content_hash, content, status, owner_id, created_at
FROM documents;

CREATE INDEX idx_document_versions_document_version
    ON document_versions (document_id, version DESC);
