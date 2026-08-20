package com.test_mcp.tibero_mcp.chaos;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SmokeEmbeddingFailureInjectorTest {

  @Test
  void 표시_문구가_든_배치만_설정한_횟수만큼_실패시키고_그_뒤에는_통과시킨다() {
    SmokeEmbeddingFailureInjector injector =
        new SmokeEmbeddingFailureInjector("[[OPENSQL_SMOKE_FAIL]]", 2);
    List<String> failedBatch = List.of("[[OPENSQL_SMOKE_FAIL]] 수동 재처리 검증 문서");

    assertThatThrownBy(() -> injector.beforeEmbedding(failedBatch))
        .isInstanceOf(SmokeEmbeddingFailureException.class);
    assertThatThrownBy(() -> injector.beforeEmbedding(failedBatch))
        .isInstanceOf(SmokeEmbeddingFailureException.class);
    assertThatCode(() -> injector.beforeEmbedding(failedBatch)).doesNotThrowAnyException();
  }

  @Test
  void 표시_문구가_없는_배치는_실패_횟수를_소진하지_않는다() {
    SmokeEmbeddingFailureInjector injector =
        new SmokeEmbeddingFailureInjector("[[OPENSQL_SMOKE_FAIL]]", 1);

    assertThatCode(() -> injector.beforeEmbedding(List.of("정상 임베딩 문서"))).doesNotThrowAnyException();
    assertThatThrownBy(() -> injector.beforeEmbedding(List.of("[[OPENSQL_SMOKE_FAIL]] 실패 주입 문서")))
        .isInstanceOf(SmokeEmbeddingFailureException.class);
  }
}
