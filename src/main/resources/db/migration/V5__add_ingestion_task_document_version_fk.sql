-- 작업이 실제 문서 버전을 가리키도록 보장한다. 존재하지 않는 버전의 작업은 생성할 수 없다.
ALTER TABLE ingestion_tasks
    ADD CONSTRAINT fk_ingestion_tasks_document_version
    FOREIGN KEY (document_id, document_version)
    REFERENCES document_versions (document_id, version);
