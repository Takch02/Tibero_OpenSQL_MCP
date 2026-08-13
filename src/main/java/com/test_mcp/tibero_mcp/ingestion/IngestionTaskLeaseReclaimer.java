package com.test_mcp.tibero_mcp.ingestion;

import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// 비정상 종료로 멈춘 PROCESSING 작업을 lease 만료 뒤 회수하는 트랜잭션 서비스다.
public class IngestionTaskLeaseReclaimer {

  private static final String LEASE_EXPIRED_MESSAGE = "Worker lease expired before completion";

  private final IngestionTaskRepository ingestionTaskRepository;
  private final IngestionLogRepository ingestionLogRepository;

  @Value("${app.embedding.worker.lease.reclaim-batch-size}")
  private int reclaimBatchSize;

  // 만료 조회와 PENDING 전이를 한 트랜잭션으로 처리해 여러 인스턴스가 같은 작업을 회수하지 않는다.
  @Transactional
  public int reclaimExpiredTasks() {
    List<IngestionTask> expiredTasks =
        ingestionTaskRepository.findExpiredProcessingForUpdateSkipLocked(reclaimBatchSize);
    for (IngestionTask task : expiredTasks) {
      task.requeueExpiredLease(LEASE_EXPIRED_MESSAGE);
      ingestionLogRepository.save(
          new IngestionLog(
              task.getDocumentId(),
              task.getDocumentVersion(),
              IngestionEvent.LEASE_EXPIRED,
              DocumentStatus.PENDING));
    }
    return expiredTasks.size();
  }
}
