package com.test_mcp.tibero_mcp.ingestion.entity;

public enum DocumentStatus {
  // 업로드 완료, 임베딩 대기(Outbox). 워커가 집어가 EMBEDDED 또는 FAILED로 전이한다.
  PENDING,
  EMBEDDED,
  FAILED,
  DELETED
}
