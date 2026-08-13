package com.test_mcp.tibero_mcp.ingestion;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
// 모델 추론 실패와 lease 만료가 같은 재시도 상한·backoff 규칙을 사용하게 한다.
public class IngestionRetryPolicy {

  private final int maxAttempts;
  private final long initialBackoffMillis;
  private final long maxBackoffMillis;

  public IngestionRetryPolicy(
      @Value("${app.embedding.worker.retry.max-attempts}") int maxAttempts,
      @Value("${app.embedding.worker.retry.initial-backoff-ms}") long initialBackoffMillis,
      @Value("${app.embedding.worker.retry.max-backoff-ms}") long maxBackoffMillis) {
    this.maxAttempts = maxAttempts;
    this.initialBackoffMillis = initialBackoffMillis;
    this.maxBackoffMillis = maxBackoffMillis;
  }

  public boolean canRetry(int attemptCount) {
    return attemptCount < maxAttempts;
  }

  public Instant nextAttemptAt(int attemptCount, Instant now) {
    long multiplier = 1L << Math.min(attemptCount - 1, 30);
    long delay =
        initialBackoffMillis > maxBackoffMillis / multiplier
            ? maxBackoffMillis
            : initialBackoffMillis * multiplier;
    return now.plus(Duration.ofMillis(delay));
  }
}
