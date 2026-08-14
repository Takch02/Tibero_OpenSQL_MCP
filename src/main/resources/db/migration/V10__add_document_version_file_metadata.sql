-- 파일은 별도 바이너리 저장소에 보관하지 않고, 버전별 파일 메타데이터와 추출 원문만 OpenSQL에 남긴다.
-- 같은 문서라도 v1 PDF와 v2 TXT처럼 입력 형식이 달라질 수 있어 documents가 아닌 document_versions에 둔다.
ALTER TABLE document_versions
    ADD COLUMN source_filename VARCHAR(255),
    ADD COLUMN source_content_type VARCHAR(100),
    ADD COLUMN source_size_bytes BIGINT,
    ADD COLUMN source_file_hash VARCHAR(64);
