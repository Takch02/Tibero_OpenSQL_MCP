-- 현재 검색 SQL은 코사인 거리 연산자(<=>)를 사용하므로 같은 연산자 클래스의 HNSW 인덱스를 둔다.
-- 아직 임베딩되지 않은 NULL 청크는 검색 대상이 아니므로 부분 인덱스에서 제외한다.
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding_hnsw_cosine
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
