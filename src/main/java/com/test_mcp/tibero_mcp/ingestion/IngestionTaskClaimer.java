package com.test_mcp.tibero_mcp.ingestion;

import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// 여러 인스턴스가 동시에 폴링해도 한 인스턴스만 작업을 lease로 점유하게 한다.
public class IngestionTaskClaimer {

  private final IngestionTaskRepository ingestionTaskRepository;

  // 애플리케이션 재기동마다 새 UUID를 부여해, 이전 프로세스의 늦은 결과 반영을 구분한다.
  private final String workerId = UUID.randomUUID().toString();

  @Value("${app.embedding.worker.lease.duration-ms}")
  private long leaseDurationMillis;

  // SELECT ... FOR UPDATE SKIP LOCKED와 PROCESSING 전이를 한 트랜잭션으로 묶어 중복 점유를 막는다.
  @Transactional
  public List<IngestionTaskClaim> claimPendingTasks(int batchSize) {
    List<IngestionTask> tasks = ingestionTaskRepository.findPendingForUpdateSkipLocked(batchSize);
    Instant now = Instant.now();
    Instant leaseExpiresAt = now.plus(Duration.ofMillis(leaseDurationMillis));

    tasks.forEach(task -> task.markProcessing(workerId, now, leaseExpiresAt));
    return tasks.stream()
        .map(
            task ->
                new IngestionTaskClaim(
                    task.getId(), task.getDocumentId(), task.getDocumentVersion(), workerId))
        .toList();
  }
}
