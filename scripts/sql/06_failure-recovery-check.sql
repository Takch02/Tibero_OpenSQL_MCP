-- OpenSQL Single failure-api 실행 뒤 document_id를 바꿔 실행한다.
-- 최종 상태뿐 아니라 FAILED와 MANUAL_RETRY 감사 이력이 남았는지 함께 확인한다.
\set document_id 1

SELECT
    d.id,
    d.version,
    d.current_search_version,
    d.status AS document_status
FROM documents d
WHERE d.id = :document_id;

SELECT
    v.version,
    v.status AS version_status,
    COUNT(c.id) AS chunk_count,
    COUNT(c.embedding) AS embedded_chunk_count
FROM document_versions v
LEFT JOIN document_chunks c
    ON c.document_id = v.document_id
   AND c.document_version = v.version
WHERE v.document_id = :document_id
GROUP BY v.version, v.status
ORDER BY v.version;

SELECT
    t.id,
    t.document_version,
    t.status AS task_status,
    t.attempt_count,
    t.last_error,
    t.claimed_by,
    t.lease_expires_at
FROM ingestion_tasks t
WHERE t.document_id = :document_id
ORDER BY t.document_version;

SELECT
    l.document_version,
    l.event,
    l.status,
    l.details,
    l.created_at
FROM ingestion_log l
WHERE l.document_id = :document_id
ORDER BY l.id;
