\pset pager off
\pset null '(null)'

-- 세션 A: 전용 smoke DB에서 실행 후 COMMIT/ROLLBACK하지 말고 잠금을 유지한다.
-- 애플리케이션은 중지한 뒤 실행해야 워커와 경합하지 않는다.
\set task_id 1

BEGIN;

SELECT t.id, t.status, t.claimed_by, t.lease_expires_at
FROM ingestion_tasks t
JOIN documents d ON d.id = t.document_id
WHERE t.id = :task_id
  AND t.status = 'PENDING'
  AND t.next_attempt_at <= CURRENT_TIMESTAMP
  AND d.deleted_at IS NULL
  AND d.version = t.document_version
FOR UPDATE OF t;
-- 기대 결과: task_id 1 row. 이 세션을 열린 채로 둔다.
