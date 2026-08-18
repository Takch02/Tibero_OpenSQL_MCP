package com.test_mcp.tibero_mcp.ingestion.file;

import com.test_mcp.tibero_mcp.ingestion.entity.SourceFileMetadata;

// 파일 입력을 기존 텍스트 기반 ingestion 흐름에 전달하기 위한 안전한 추출 결과다.
public record ExtractedFile(
    String filename, String contentType, long sizeBytes, String fileHash, String content) {

  // 추출 계층의 파일 정보를 영속 도메인이 보관할 메타데이터로 변환한다.
  public SourceFileMetadata toSourceFileMetadata() {
    return new SourceFileMetadata(filename, contentType, sizeBytes, fileHash);
  }
}
