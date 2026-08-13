package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IngestionFailureSummaryTest {

  @Test
  void 예외_원문을_저장하지_않고_안전한_입력_오류_코드로_정규화한다() {
    String sensitiveMessage = "Bearer secret-token 문서 원문 user@example.com";

    IngestionFailureSummary summary =
        IngestionFailureSummary.from(new IllegalArgumentException(sensitiveMessage));

    assertThat(summary.code()).isEqualTo("EMBEDDING_INPUT_INVALID");
    assertThat(summary.toStorageValue()).doesNotContain(sensitiveMessage);
  }

  @Test
  void 알_수_없는_추론_오류는_일반_처리_오류_코드로_정규화한다() {
    String sensitiveMessage = "Bearer secret-token 문서 원문 user@example.com";

    IngestionFailureSummary summary =
        IngestionFailureSummary.from(new RuntimeException(sensitiveMessage));

    assertThat(summary.code()).isEqualTo("EMBEDDING_INFERENCE_FAILED");
    assertThat(summary.toStorageValue()).doesNotContain(sensitiveMessage);
  }
}
