package com.test_mcp.tibero_mcp.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// toVectorLiteral은 임베딩 모델·DB 없이도 검증 가능한 순수 포맷 변환이라 단위테스트로 다룬다.
// pgvector가 요구하는 "[v1,v2,...]" 형식과 정확히 일치해야 하므로 형식 회귀를 여기서 잡는다.
class EmbeddingServiceTest {

  private final EmbeddingService embeddingService = new EmbeddingService(null);

  @Test
  void float_배열을_pgvector_리터럴_형식으로_변환한다() {
    String literal = embeddingService.toVectorLiteral(new float[] {0.1f, 0.2f, -0.3f});

    assertThat(literal).isEqualTo("[0.1,0.2,-0.3]");
  }

  @Test
  void 빈_배열은_빈_대괄호로_변환된다() {
    String literal = embeddingService.toVectorLiteral(new float[] {});

    assertThat(literal).isEqualTo("[]");
  }
}
