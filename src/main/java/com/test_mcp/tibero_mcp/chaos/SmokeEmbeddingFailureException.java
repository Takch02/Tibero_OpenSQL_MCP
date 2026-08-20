package com.test_mcp.tibero_mcp.chaos;

// API 경계까지 전파되지 않고 EmbeddingWorker가 안전한 실패 요약으로 변환하는 smoke 전용 예외다.
public class SmokeEmbeddingFailureException extends RuntimeException {

  public SmokeEmbeddingFailureException(int remainingBeforeAttempt) {
    super("OpenSQL smoke embedding failure injected. remaining=" + remainingBeforeAttempt);
  }
}
