\set ON_ERROR_STOP on
\pset pager off
\timing on

-- 생성 시간은 psql의 Timing 출력으로 기록한다. 실제 애플리케이션 인덱스와 같은 코사인 연산자 클래스를 쓴다.
CREATE INDEX idx_vector_benchmark_embedding_hnsw
    ON vector_benchmark_chunks
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

ANALYZE vector_benchmark_chunks;

SELECT set_config('vector_benchmark.query_embedding', embedding::TEXT, false)
FROM vector_benchmark_chunks
WHERE id = 1;

SELECT pg_size_pretty(pg_relation_size('idx_vector_benchmark_embedding_hnsw')) AS hnsw_index_size,
       pg_size_pretty(pg_relation_size('vector_benchmark_chunks')) AS table_size;

\echo '=== HNSW: no metadata filter ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.id, c.document_id, c.document_version
FROM vector_benchmark_chunks c
JOIN vector_benchmark_documents d ON d.id = c.document_id
WHERE d.deleted_at IS NULL
  AND d.current_search_version = c.document_version
  AND c.embedding IS NOT NULL
ORDER BY c.embedding <=> current_setting('vector_benchmark.query_embedding')::vector
LIMIT 10;

\echo '=== HNSW: owner filter ==='
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

\echo '=== HNSW: owner + category filter ==='
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

-- 정확한 baseline top-10에 포함된 개수만 계산한다. 10이면 이 한 쿼리의 recall@10은 1.0이다.
WITH hnsw_top10 AS (
    SELECT c.id
    FROM vector_benchmark_chunks c
    JOIN vector_benchmark_documents d ON d.id = c.document_id
    WHERE d.deleted_at IS NULL
      AND d.current_search_version = c.document_version
      AND c.embedding IS NOT NULL
    ORDER BY c.embedding <=> current_setting('vector_benchmark.query_embedding')::vector
    LIMIT 10
)
SELECT COUNT(*) AS matching_top10_count,
       COUNT(*)::DECIMAL / 10 AS recall_at_10
FROM hnsw_top10 h
JOIN vector_benchmark_exact_top10 e ON e.id = h.id;
