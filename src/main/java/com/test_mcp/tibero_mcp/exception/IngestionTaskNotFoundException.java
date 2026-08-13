package com.test_mcp.tibero_mcp.exception;

public class IngestionTaskNotFoundException extends TiberoMcpException {

  public IngestionTaskNotFoundException(Long taskId) {
    super(ErrorCode.INGESTION_TASK_NOT_FOUND, "Ingestion 작업을 찾을 수 없습니다. taskId=" + taskId);
  }
}
