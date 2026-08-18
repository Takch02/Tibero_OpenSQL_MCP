\pset pager off
\pset null '(null)'
\timing on

-- 세션 B: 세션 A가 같은 task_id를 잠근 동안 실행한다.
\set task_id 1

BEGIN;

-- Repository의 claim SQL과 같은 조건에 task_id만 추가했다.
SELECT t.id
FROM ingestion_tasks t
JOIN documents d ON d.id = t.document_id
WHERE t.id = :task_id
  AND t.status = 'PENDING'
  AND t.next_attempt_at <= CURRENT_TIMESTAMP
  AND d.deleted_at IS NULL
  AND d.version = t.document_version
ORDER BY t.id
LIMIT 1
FOR UPDATE OF t SKIP LOCKED;
-- 기대 결과: 짧은 시간 안에 0 rows. 세션 A가 끝날 때까지 대기하면 실패다.

ROLLBACK;
