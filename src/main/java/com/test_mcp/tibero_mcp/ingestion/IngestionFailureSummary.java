package com.test_mcp.tibero_mcp.ingestion;

// 예외 원문에는 토큰·문서 내용 등이 들어갈 수 있어 DB 감사 이력에는 허용된 코드와 요약만 남긴다.
public record IngestionFailureSummary(String code, String message) {

  private static final IngestionFailureSummary INVALID_INPUT =
      new IngestionFailureSummary("EMBEDDING_INPUT_INVALID", "임베딩 요청의 입력 형식 또는 크기가 올바르지 않습니다.");
  private static final IngestionFailureSummary INFERENCE_FAILED =
      new IngestionFailureSummary("EMBEDDING_INFERENCE_FAILED", "임베딩 모델 처리 중 오류가 발생했습니다.");

  public static IngestionFailureSummary from(RuntimeException exception) {
    return exception instanceof IllegalArgumentException ? INVALID_INPUT : INFERENCE_FAILED;
  }

  public String toStorageValue() {
    return code + ": " + message;
  }
}
