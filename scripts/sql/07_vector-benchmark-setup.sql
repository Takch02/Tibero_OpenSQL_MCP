\set ON_ERROR_STOP on
\pset pager off
\timing on

-- 실행 예: psql -v chunk_count=10000 -f scripts/sql/07_vector-benchmark-setup.sql
-- 애플리케이션 테이블과 분리된 전용 테이블만 재생성한다.
DROP TABLE IF EXISTS vector_benchmark_exact_top10;
DROP TABLE IF EXISTS vector_benchmark_chunks;
DROP TABLE IF EXISTS vector_benchmark_documents;

CREATE TABLE vector_benchmark_documents (
    id                     BIGINT PRIMARY KEY,
    owner_id               VARCHAR(255) NOT NULL,
    category               VARCHAR(255) NOT NULL,
    current_search_version INTEGER NOT NULL,
    deleted_at             TIMESTAMP WITH TIME ZONE
);

CREATE TABLE vector_benchmark_chunks (
    id               BIGINT PRIMARY KEY,
    document_id      BIGINT NOT NULL REFERENCES vector_benchmark_documents (id),
    document_version INTEGER NOT NULL,
    chunk_index      INTEGER NOT NULL,
    embedding        vector(384)
);

-- 10개 문서에 owner/category를 분산해 무필터·owner 필터·owner+category 필터를 모두 측정한다.
INSERT INTO vector_benchmark_documents (id, owner_id, category, current_search_version)
SELECT document_number,
       'benchmark-owner-' || ((document_number - 1) % 2 + 1),
       'benchmark-category-' || ((document_number - 1) % 3 + 1),
       1
FROM generate_series(1, 10) AS documents(document_number);

-- 외부 모델 호출 없이 청크마다 다른 결정적 384차원 벡터를 만든다.
-- 성능 비교용 데이터이므로 이 벡터의 의미 품질을 주장하지 않는다.
INSERT INTO vector_benchmark_chunks (id, document_id, document_version, chunk_index, embedding)
SELECT chunk_numbers.number,
       ((chunk_numbers.number - 1) % 10) + 1,
       1,
       chunk_numbers.number - 1,
       ARRAY(
           SELECT ((SIN(chunk_numbers.number::DOUBLE PRECISION * dimensions.dimension) + 1) / 2)::REAL
           FROM generate_series(1, 384) AS dimensions(dimension)
       )::vector
FROM generate_series(1, :chunk_count) AS chunk_numbers(number);

CREATE INDEX idx_vector_benchmark_documents_access
    ON vector_benchmark_documents (owner_id, category, id);

ANALYZE vector_benchmark_documents;
ANALYZE vector_benchmark_chunks;

SELECT :chunk_count::INTEGER AS generated_chunk_count,
       COUNT(*) AS actual_chunk_count,
       MIN(vector_dims(embedding)) AS min_embedding_dimension,
       MAX(vector_dims(embedding)) AS max_embedding_dimension
FROM vector_benchmark_chunks;
