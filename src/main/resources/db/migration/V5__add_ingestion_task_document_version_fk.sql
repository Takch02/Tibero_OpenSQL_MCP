-- 이후 INSERT/UPDATE의 참조 무결성을 먼저 강제하고, 기존 행 전체 검증은 다음 마이그레이션으로 분리한다.
ALTER TABLE ingestion_tasks
    ADD CONSTRAINT fk_ingestion_tasks_document_version
    FOREIGN KEY (document_id, document_version)
    REFERENCES document_versions (document_id, version)
    NOT VALID;
