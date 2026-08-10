-- V5에서 미룬 기존 작업 행의 문서 버전 참조를 검증한다.
ALTER TABLE ingestion_tasks
    VALIDATE CONSTRAINT fk_ingestion_tasks_document_version;
