\set ON_ERROR_STOP on
\pset pager off
\timing on

DROP INDEX IF EXISTS idx_vector_benchmark_embedding_hnsw;
ANALYZE vector_benchmark_chunks;

SELECT set_config('vector_benchmark.query_embedding', embedding::TEXT, false)
FROM vector_benchmark_chunks
WHERE id = 1;

-- HNSW 도입 전 exact top-10을 저장한다. 이후 approximate HNSW 결과의 recall 기준이다.
CREATE TABLE vector_benchmark_exact_top10 AS
SELECT c.id
FROM vector_benchmark_chunks c
JOIN vector_benchmark_documents d ON d.id = c.document_id
WHERE d.deleted_at IS NULL
  AND d.current_search_version = c.document_version
  AND c.embedding IS NOT NULL
ORDER BY c.embedding <=> current_setting('vector_benchmark.query_embedding')::vector
LIMIT 10;

\echo '=== baseline: no metadata filter ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.id, c.document_id, c.document_version
FROM vector_benchmark_chunks c
JOIN vector_benchmark_documents d ON d.id = c.document_id
WHERE d.deleted_at IS NULL
  AND d.current_search_version = c.document_version
  AND c.embedding IS NOT NULL
ORDER BY c.embedding <=> current_setting('vector_benchmark.query_embedding')::vector
LIMIT 10;

\echo '=== baseline: owner filter ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.id, c.document_id, c.document_version
FROM vector_benchmark_chunks c
JOIN vector_benchmark_documents d ON d.id = c.document_id
WHERE d.owner_id = 'benchmark-owner-1'
  AND d.deleted_at IS NULL
  AND d.current_search_version = c.document_version
  AND c.embedding IS NOT NULL
ORDER BY c.embedding <=> current_setting('vector_benchmark.query_embedding')::vector
LIMIT 10;

\echo '=== baseline: owner + category filter ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.id, c.document_id, c.document_version
FROM vector_benchmark_chunks c
JOIN vector_benchmark_documents d ON d.id = c.document_id
WHERE d.owner_id = 'benchmark-owner-1'
  AND d.category = 'benchmark-category-1'
  AND d.deleted_at IS NULL
  AND d.current_search_version = c.document_version
  AND c.embedding IS NOT NULL
ORDER BY c.embedding <=> current_setting('vector_benchmark.query_embedding')::vector
LIMIT 10;
