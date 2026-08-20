package com.test_mcp.tibero_mcp.ingestion;

import com.test_mcp.tibero_mcp.chaos.EmbeddingFailureInjector;
import com.test_mcp.tibero_mcp.embedding.EmbeddingService;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentChunk;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Outbox 소비자. PENDING 문서를 폴링해 청크를 배치 추론하고 결과만 짧은 트랜잭션으로 반영한다.
// 느린 모델 추론을 트랜잭션 밖에 두는 것이 P1(트랜잭션이 오래 열려 failover에 통째로 날아가는 것 방지)의 핵심.
@Component
@RequiredArgsConstructor
public class EmbeddingWorker {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);

  private final DocumentChunkRepository documentChunkRepository;
  private final EmbeddingService embeddingService;
  private final EmbeddingFailureInjector embeddingFailureInjector;
  private final EmbeddingResultWriter embeddingResultWriter;
  private final IngestionTaskClaimer ingestionTaskClaimer;

  @Value("${app.embedding.worker.batch-size}")
  private int batchSize;

  // 한 번의 embedAll() 호출에 넣을 청크 수 상한. 수백 페이지 PDF처럼 청크가 매우 많은 문서를
  // 한 번에 통째로 추론시키면 요청이 비대해지고, 실패 시 전체가 롤백되는 위험이 커진다.
  @Value("${app.embedding.worker.embed-batch-size}")
  private int embedBatchSize;

  /** IngestionTask(outbox)를 조회하여 비동기로 임베딩 실시 */
  @Scheduled(
      fixedDelayString = "${app.embedding.worker.poll-interval-ms}",
      initialDelayString = "${app.embedding.worker.initial-delay-ms}")
  public void pollAndProcess() {
    List<IngestionTaskClaim> claimedTasks = ingestionTaskClaimer.claimPendingTasks(batchSize);
    for (IngestionTaskClaim task : claimedTasks) {
      try {
        process(task);
      } catch (RuntimeException e) {
        // 이미 claim한 다른 작업은 lease 만료까지 정체되지 않도록 작업 단위로 예외를 격리한다.
        log.warn("작업 처리 중 예외로 다음 작업을 계속 진행합니다. taskId={}", task.taskId(), e);
      }
    }
  }

  // 문서 하나를 처리한다. 추론(느림)은 여기서 트랜잭션 없이 수행하고, DB 반영만 별도 트랜잭션 빈에 위임한다.
  void process(IngestionTaskClaim task) {
    if (!renewLease(task)) {
      return;
    }
    List<DocumentChunk> chunks =
        documentChunkRepository
            .findByDocumentIdAndDocumentVersionAndEmbeddingIsNullOrderByChunkIndexAsc(
                task.documentId(), task.documentVersion());
    if (chunks.isEmpty()) {
      // 이미 다 임베딩된 문서 — 상태만 정리한다.
      completeEmbedding(task);
      return;
    }

    try {
      if (!embedAndSaveInBatches(task, chunks)) {
        return;
      }
      completeEmbedding(task);
    } catch (RuntimeException e) {
      // 이미 저장된 청크는 유지하고, 아직 NULL인 청크만 다음 재시도에서 처리한다.
      IngestionFailureSummary failure = IngestionFailureSummary.from(e);
      log.warn("문서 임베딩 실패 (documentId={}, failureCode={})", task.documentId(), failure.code());
      if (!embeddingResultWriter.handleFailure(
          task.taskId(), task.documentId(), task.documentVersion(), task.workerId(), failure)) {
        log.info("lease를 잃은 작업의 실패 처리를 건너뜁니다. taskId={}", task.taskId());
      }
    }
  }

  // 배치별 결과를 즉시 저장해, 다음 재시도에서 이미 성공한 청크를 다시 추론하지 않는다.
  private boolean embedAndSaveInBatches(IngestionTaskClaim task, List<DocumentChunk> chunks) {
    for (int start = 0; start < chunks.size(); start += embedBatchSize) {
      // 느린 추론 전에 lease를 연장하고, 이미 회수된 작업이면 모델 호출 자체를 중단한다.
      if (!renewLease(task)) {
        return false;
      }
      int end = Math.min(start + embedBatchSize, chunks.size());
      List<DocumentChunk> batch = chunks.subList(start, end);
      // opensql-smoke 프로필에서만 실제 handleFailure 경로를 확인하기 위해 모델 호출 직전에 실패를 주입한다.
      embeddingFailureInjector.beforeEmbedding(
          batch.stream().map(DocumentChunk::getContent).toList());
      List<float[]> vectors =
          embeddingService.embedAll(batch.stream().map(DocumentChunk::getContent).toList());
      List<Long> chunkIds = batch.stream().map(DocumentChunk::getId).toList();
      List<String> vectorLiterals =
          vectors.stream().map(embeddingService::toVectorLiteral).toList();
      embeddingResultWriter.saveEmbeddings(chunkIds, vectorLiterals);
      // 저장 뒤에도 소유권을 확인해 다음 배치가 회수된 작업을 계속 처리하지 않게 한다.
      if (!renewLease(task)) {
        return false;
      }
    }
    return true;
  }

  // 완료 상태 전이는 현재 lease 소유자에게만 허용한다. false는 회수된 옛 워커라는 뜻이다.
  private void completeEmbedding(IngestionTaskClaim task) {
    if (!embeddingResultWriter.completeEmbedding(
        task.taskId(), task.documentId(), task.documentVersion(), task.workerId())) {
      log.info("lease를 잃은 작업의 완료 처리를 건너뜁니다. taskId={}", task.taskId());
    }
  }

  // heartbeat는 짧은 별도 트랜잭션으로 갱신해 모델 추론 동안 DB 잠금을 오래 잡지 않는다.
  private boolean renewLease(IngestionTaskClaim task) {
    boolean renewed = embeddingResultWriter.renewLease(task.taskId(), task.workerId());
    if (!renewed) {
      log.info("lease를 잃은 작업 처리를 중단합니다. taskId={}", task.taskId());
    }
    return renewed;
  }
}
