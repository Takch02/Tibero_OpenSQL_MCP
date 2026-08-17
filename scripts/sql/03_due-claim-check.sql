\pset pager off
\pset null '(null)'

-- 전용 smoke DB에서만 실행한다. 아래 task_id를 현재 PENDING 작업 ID로 바꾼다.
-- 애플리케이션은 중지한 뒤 실행해야 워커와 경합하지 않는다.
\set task_id 1

BEGIN;

-- 미래 retry 시각이면 claim 대상에서 제외되어야 한다.
UPDATE ingestion_tasks
SET status = 'PENDING',
    next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '10 minutes'
WHERE id = :task_id;

SELECT t.id
FROM ingestion_tasks t
JOIN documents d ON d.id = t.document_id
WHERE t.id = :task_id
  AND t.status = 'PENDING'
  AND t.next_attempt_at <= CURRENT_TIMESTAMP
  AND d.deleted_at IS NULL
  AND d.version = t.document_version
FOR UPDATE OF t SKIP LOCKED;
-- 기대 결과: 0 rows

-- due 시각이 과거면 같은 claim 조건에서 작업을 반환해야 한다.
UPDATE ingestion_tasks
SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
WHERE id = :task_id;

SELECT t.id, t.status, t.next_attempt_at
FROM ingestion_tasks t
JOIN documents d ON d.id = t.document_id
WHERE t.id = :task_id
  AND t.status = 'PENDING'
  AND t.next_attempt_at <= CURRENT_TIMESTAMP
  AND d.deleted_at IS NULL
  AND d.version = t.document_version
FOR UPDATE OF t SKIP LOCKED;
-- 기대 결과: task_id 1 row

ROLLBACK;
-- 원래 retry metadata를 보존한다.
