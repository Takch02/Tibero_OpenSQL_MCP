package com.test_mcp.tibero_mcp.exception;

import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;

public class IngestionRetryConflictException extends TiberoMcpException {

  public IngestionRetryConflictException(
      Long documentId, Integer documentVersion, IngestionTaskStatus taskStatus) {
    super(
        ErrorCode.INGESTION_RETRY_CONFLICT,
        "최종 실패한 최신 작업만 수동 재처리할 수 있습니다. documentId="
            + documentId
            + ", documentVersion="
            + documentVersion
            + ", taskStatus="
            + taskStatus);
  }
}
