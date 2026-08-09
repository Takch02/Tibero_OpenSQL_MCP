CREATE EXTENSION IF NOT EXISTS vector;

-- 벡터 차원(384)은 all-MiniLM-L6-v2 임베딩 모델의 출력 차원(app.embedding.dimension)과 일치해야 한다.
-- 모델을 교체해 차원이 바뀌면 새 마이그레이션(ALTER COLUMN)으로 반영한다.

-- 문서 원본. idempotency_key는 같은 요청의 재시도를 막고(UNIQUE),
-- content_hash는 다른 요청이라도 동일 내용이면 재처리를 스킵하는 데 쓴다.
CREATE TABLE IF NOT EXISTS documents (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    content_hash    VARCHAR(64)  NOT NULL,
    title           VARCHAR(255),
    content         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    version         INTEGER      NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_documents_content_hash ON documents (content_hash);

-- 청크. 업로드 시점에는 embedding을 NULL로 남기고, 임베딩은 트랜잭션 밖에서 별도 처리한다(Outbox 패턴).
CREATE TABLE IF NOT EXISTS document_chunks (
    id                BIGSERIAL PRIMARY KEY,
    document_id       BIGINT REFERENCES documents (id),
    document_version  INTEGER      NOT NULL,
    chunk_index       INTEGER      NOT NULL,
    content           TEXT         NOT NULL,
    embedding         vector(384),
    UNIQUE (document_id, document_version, chunk_index)
);

-- 수집 이벤트 로그. 업로드 트랜잭션에 함께 묶여 "저장은 됐는데 처리 예약이 안 됨" 상태를 방지한다.
CREATE TABLE IF NOT EXISTS ingestion_log (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT      REFERENCES documents (id),
    event       VARCHAR(30) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);
