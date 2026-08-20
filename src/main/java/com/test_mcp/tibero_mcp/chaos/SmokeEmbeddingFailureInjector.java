package com.test_mcp.tibero_mcp.chaos;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * OpenSQL smoke 프로필에서 표시 문구가 든 배치만 정해진 횟수 실패시킨다. 횟수가 소진되면 같은 문서를 수동 재처리해도 정상 임베딩되므로, 실패·복구의 실제 워커
 * 경로를 한 번의 실행으로 검증할 수 있다.
 */
@Component
@Profile("opensql-smoke")
public class SmokeEmbeddingFailureInjector implements EmbeddingFailureInjector {

  private final String failureMarker;
  private final AtomicInteger remainingFailures;

  public SmokeEmbeddingFailureInjector(
      @Value("${app.embedding.chaos.failure-marker}") String failureMarker,
      @Value("${app.embedding.chaos.fail-first-batches}") int failFirstBatches) {
    this.failureMarker = failureMarker;
    this.remainingFailures = new AtomicInteger(failFirstBatches);
  }

  @Override
  public void beforeEmbedding(List<String> contents) {
    if (contents.stream().noneMatch(content -> content.contains(failureMarker))) {
      return;
    }

    int remainingBeforeAttempt =
        remainingFailures.getAndUpdate(remaining -> Math.max(remaining - 1, 0));
    if (remainingBeforeAttempt > 0) {
      throw new SmokeEmbeddingFailureException(remainingBeforeAttempt);
    }
  }
}
