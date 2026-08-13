package com.test_mcp.tibero_mcp.ingestion.dto;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "문서 최신 버전의 임베딩 처리 및 재시도 상태")
public record IngestionStatusResponse(
    @Schema(description = "문서 ID") Long documentId,
    @Schema(description = "최신 작성 버전") Integer version,
    @Schema(description = "현재 검색에 노출되는 마지막 정상 임베딩 버전") Integer currentSearchVersion,
    @Schema(description = "최신 문서 버전의 처리 상태") String documentStatus,
    @Schema(description = "Outbox 작업 상태") String taskStatus,
    @Schema(description = "현재 작업의 임베딩 시도 횟수") int attemptCount,
    @Schema(description = "다음 재시도 가능 시각") Instant nextAttemptAt,
    @Schema(description = "마지막 임베딩 실패 원인") String lastError,
    @Schema(description = "최신 버전의 전체 청크 수") long chunkCount,
    @Schema(description = "최신 버전에서 임베딩이 완료된 청크 수") long embeddedChunkCount) {

  public static IngestionStatusResponse from(
      Document document, IngestionTask task, long chunkCount, long embeddedChunkCount) {
    return new IngestionStatusResponse(
        document.getId(),
        document.getVersion(),
        document.getCurrentSearchVersion(),
        document.getStatus().name(),
        task.getStatus().name(),
        task.getAttemptCount(),
        task.getNextAttemptAt(),
        task.getLastError(),
        chunkCount,
        embeddedChunkCount);
  }
}
