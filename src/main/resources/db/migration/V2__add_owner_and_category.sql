-- 정형 데이터(권한, 메타데이터)를 벡터 검색과 한 SQL에서 함께 거르기 위한 컬럼.
-- owner_id: 문서 소유자. 검색은 반드시 owner_id로 필터링해 접근 권한이 없는 문서를 걸러낸다.
-- category: 정형 메타데이터 필터 예시(문서 분류).
ALTER TABLE documents ADD COLUMN owner_id VARCHAR(255) NOT NULL;
ALTER TABLE documents ADD COLUMN category VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_documents_owner_category ON documents (owner_id, category);
