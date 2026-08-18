package com.test_mcp.tibero_mcp.ingestion.dto;

import com.test_mcp.tibero_mcp.ingestion.entity.DocumentVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "문서의 특정 버전 원문과 처리 상태")
public record DocumentVersionResponse(
    @Schema(description = "버전 번호") Integer version,
    @Schema(description = "원문 내용") String content,
    @Schema(description = "임베딩 처리 상태") String status,
    @Schema(description = "버전을 만든 사용자") String createdBy,
    @Schema(description = "파일 업로드 원본 파일명. JSON 본문 입력 버전이면 null") String sourceFilename,
    @Schema(description = "검증된 원본 파일 MIME 타입. JSON 본문 입력 버전이면 null") String sourceContentType,
    @Schema(description = "원본 파일 바이트 크기. JSON 본문 입력 버전이면 null") Long sourceSizeBytes,
    @Schema(description = "원본 파일 SHA-256. JSON 본문 입력 버전이면 null") String sourceFileHash,
    @Schema(description = "버전 생성 시각") Instant createdAt) {

  public static DocumentVersionResponse from(DocumentVersion documentVersion) {
    return new DocumentVersionResponse(
        documentVersion.getVersion(),
        documentVersion.getContent(),
        documentVersion.getStatus().name(),
        documentVersion.getCreatedBy(),
        documentVersion.getSourceFilename(),
        documentVersion.getSourceContentType(),
        documentVersion.getSourceSizeBytes(),
        documentVersion.getSourceFileHash(),
        documentVersion.getCreatedAt());
  }
}
