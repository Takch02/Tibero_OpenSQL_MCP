package com.test_mcp.tibero_mcp.ingestion.dto;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문서 업로드 응답")
public record UploadResponse(
    @Schema(description = "문서 ID") Long documentId,
    @Schema(description = "처리 상태 (PENDING/EMBEDDED/FAILED)") String status,
    @Schema(description = "문서 버전") Integer version) {

  public static UploadResponse from(Document document) {
    return new UploadResponse(document.getId(), document.getStatus().name(), document.getVersion());
  }
}
