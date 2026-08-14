package com.test_mcp.tibero_mcp.ingestion.entity;

// 파일에서 생성된 문서 버전에만 붙는 불변 원본 메타데이터다. 원본 파일 바이트는 저장하지 않는다.
public record SourceFileMetadata(
    String filename, String contentType, Long sizeBytes, String fileHash) {}
