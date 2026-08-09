package com.test_mcp.tibero_mcp.ingestion.dto;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "문서의 현재 메타데이터와 동기화 상태")
public record DocumentResponse(
    @Schema(description = "문서 ID") Long documentId,
    @Schema(description = "문서 제목") String title,
    @Schema(description = "소유자 ID") String ownerId,
    @Schema(description = "문서 분류") String category,
    @Schema(description = "최신 작성 버전") Integer version,
    @Schema(description = "현재 검색에 노출되는 마지막 정상 임베딩 버전") Integer currentSearchVersion,
    @Schema(description = "현재 최신 버전의 처리 상태") String status,
    @Schema(description = "문서 생성 시각") Instant createdAt) {

  public static DocumentResponse from(Document document) {
    return new DocumentResponse(
        document.getId(),
        document.getTitle(),
        document.getOwnerId(),
        document.getCategory(),
        document.getVersion(),
        document.getCurrentSearchVersion(),
        document.getStatus().name(),
        document.getCreatedAt());
  }
}
