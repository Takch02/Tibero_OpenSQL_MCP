\pset pager off
\pset null '(null)'

-- vector(384)과 재시도 시간 컬럼의 실제 타입을 확인한다.
SELECT c.relname AS table_name,
       a.attname AS column_name,
       format_type(a.atttypid, a.atttypmod) AS column_type,
       a.attnotnull AS not_null
FROM pg_attribute a
JOIN pg_class c ON c.oid = a.attrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relname IN ('document_chunks', 'ingestion_tasks')
  AND a.attname IN ('embedding', 'next_attempt_at', 'heartbeat_at', 'lease_expires_at')
  AND a.attnum > 0
  AND NOT a.attisdropped
ORDER BY c.relname, a.attname;

-- ingestion_tasks가 문서 버전을 복합 외래 키로 참조하는지 확인한다.
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'ingestion_tasks'::regclass
  AND conname = 'fk_ingestion_tasks_document_version';

-- API smoke를 실행한 뒤에는 embedding 차원이 384인지도 확인한다.
SELECT document_id,
       document_version,
       chunk_index,
       vector_dims(embedding) AS embedding_dimension
FROM document_chunks
WHERE embedding IS NOT NULL
ORDER BY id DESC
LIMIT 10;
