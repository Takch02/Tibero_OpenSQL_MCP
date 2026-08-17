\pset pager off
\pset null '(null)'

-- 전용 smoke DB에서만 실행한다. API smoke가 만든 최신 작업 ID로 바꾼다.
-- 이후 애플리케이션을 중지한 상태에서 due/SKIP LOCKED 쿼리를 검증한다.
\set task_id 4

BEGIN;

-- 실제 Repository claim 조건을 만족하는 테스트 행을 준비한다.
UPDATE ingestion_tasks
SET status = 'PENDING',
    next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second',
    claimed_by = NULL,
    heartbeat_at = NULL,
    lease_expires_at = NULL
WHERE id = :task_id;

SELECT t.id, t.status, t.next_attempt_at, d.version, t.document_version
FROM ingestion_tasks t
JOIN documents d ON d.id = t.document_id
WHERE t.id = :task_id;
-- 기대 결과: status=PENDING, next_attempt_at이 과거, version=document_version

COMMIT;
