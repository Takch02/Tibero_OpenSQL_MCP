package com.test_mcp.tibero_mcp.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
// 스케줄 실행과 회수 트랜잭션을 분리해 @Transactional 프록시를 반드시 거치게 한다.
public class IngestionTaskLeaseReclaimScheduler {

  private final IngestionTaskLeaseReclaimer ingestionTaskLeaseReclaimer;

  // 즉시 회수하지 않고 주기적으로 검사해, 일시적으로 느린 모델 추론과 중복 실행을 구분한다.
  @Scheduled(
      fixedDelayString = "${app.embedding.worker.lease.reclaim-interval-ms}",
      initialDelayString = "${app.embedding.worker.initial-delay-ms}")
  public void reclaimExpiredTasksOnSchedule() {
    ingestionTaskLeaseReclaimer.reclaimExpiredTasks();
  }
}
