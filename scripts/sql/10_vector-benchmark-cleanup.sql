\set ON_ERROR_STOP on

-- 측정 전용 테이블만 삭제한다. 애플리케이션 테이블과 Flyway 마이그레이션에는 영향을 주지 않는다.
DROP TABLE IF EXISTS vector_benchmark_exact_top10;
DROP TABLE IF EXISTS vector_benchmark_chunks;
DROP TABLE IF EXISTS vector_benchmark_documents;
