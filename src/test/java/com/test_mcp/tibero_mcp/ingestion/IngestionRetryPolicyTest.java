package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IngestionRetryPolicyTest {

  private final IngestionRetryPolicy retryPolicy = new IngestionRetryPolicy(3, 1_000, 60_000);

  @Test
  void 시도_횟수가_상한에_도달하기_전까지만_재시도한다() {
    assertThat(retryPolicy.canRetry(1)).isTrue();
    assertThat(retryPolicy.canRetry(2)).isTrue();
    assertThat(retryPolicy.canRetry(3)).isFalse();
  }

  @Test
  void 재시도_대기_시간은_지수적으로_증가하고_상한을_넘지_않는다() {
    Instant now = Instant.parse("2026-08-13T00:00:00Z");

    assertThat(retryPolicy.nextAttemptAt(1, now)).isEqualTo(now.plusSeconds(1));
    assertThat(retryPolicy.nextAttemptAt(2, now)).isEqualTo(now.plusSeconds(2));
    assertThat(retryPolicy.nextAttemptAt(10, now)).isEqualTo(now.plusSeconds(60));
  }
}
