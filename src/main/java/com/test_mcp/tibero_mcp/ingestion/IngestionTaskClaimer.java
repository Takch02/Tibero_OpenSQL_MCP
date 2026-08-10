package com.test_mcp.tibero_mcp.ingestion;

import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngestionTaskClaimer {

  private final IngestionTaskRepository ingestionTaskRepository;

  // 점유 상태 변경을 같은 트랜잭션으로 커밋해야 다른 워커가 동일 작업을 다시 가져가지 않는다.
  @Transactional
  public List<IngestionTaskClaim> claimPendingTasks(int batchSize) {
    List<IngestionTask> tasks = ingestionTaskRepository.findPendingForUpdateSkipLocked(batchSize);
    tasks.forEach(IngestionTask::markProcessing);
    return tasks.stream()
        .map(
            task ->
                new IngestionTaskClaim(
                    task.getId(), task.getDocumentId(), task.getDocumentVersion()))
        .toList();
  }
}
