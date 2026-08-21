package com.test_mcp.tibero_mcp.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

// toVectorLiteral은 임베딩 모델·DB 없이도 검증 가능한 순수 포맷 변환이라 단위테스트로 다룬다.
// pgvector가 요구하는 "[v1,v2,...]" 형식과 정확히 일치해야 하므로 형식 회귀를 여기서 잡는다.
class EmbeddingServiceTest {

  private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
  private final EmbeddingService embeddingService =
      new EmbeddingService(embeddingModel, new EmbeddingPrefixProperties("passage: ", "query: "));

  EmbeddingServiceTest() {
    ReflectionTestUtils.setField(embeddingService, "dimension", 3);
  }

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

  @Test
  void 접두사_설정이_없으면_빈_문자열로_정규화한다() {
    EmbeddingPrefixProperties prefixes = new EmbeddingPrefixProperties(null, null);

    assertThat(prefixes.document()).isEmpty();
    assertThat(prefixes.query()).isEmpty();
  }

  @Test
  void 문서_배치에는_문서_접두사를_붙여_추론한다() {
    given(embeddingModel.embed(List.of("passage: 관리자 계정 정책")))
        .willReturn(List.of(new float[] {0.1f, 0.2f, 0.3f}));

    embeddingService.embedDocuments(List.of("관리자 계정 정책"));

    verify(embeddingModel).embed(List.of("passage: 관리자 계정 정책"));
  }

  @Test
  void 검색_질의에는_질의_접두사를_붙여_추론한다() {
    given(embeddingModel.embed("query: 관리자 계정 인증 수단")).willReturn(new float[] {0.1f, 0.2f, 0.3f});

    embeddingService.embedQuery("관리자 계정 인증 수단");

    verify(embeddingModel).embed("query: 관리자 계정 인증 수단");
  }
}
