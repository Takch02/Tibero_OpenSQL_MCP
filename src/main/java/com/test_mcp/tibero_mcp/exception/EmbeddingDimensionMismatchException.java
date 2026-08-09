package com.test_mcp.tibero_mcp.exception;

// 모델이 반환한 벡터 차원이 app.embedding.dimension 설정과 다른 설정 오류. GlobalExceptionHandler가 500으로 매핑한다.
public class EmbeddingDimensionMismatchException extends TiberoMcpException {

  public EmbeddingDimensionMismatchException(int actual, int expected) {
    super("임베딩 차원(%d)이 설정값(app.embedding.dimension=%d)과 다릅니다.".formatted(actual, expected));
  }
}
