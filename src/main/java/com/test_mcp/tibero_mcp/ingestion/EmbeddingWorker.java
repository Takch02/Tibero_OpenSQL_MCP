package com.test_mcp.tibero_mcp.ingestion;

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
      process(task);
    }
  }

  // 문서 하나를 처리한다. 추론(느림)은 여기서 트랜잭션 없이 수행하고, DB 반영만 별도 트랜잭션 빈에 위임한다.
  void process(IngestionTaskClaim task) {
    List<DocumentChunk> chunks =
        documentChunkRepository
            .findByDocumentIdAndDocumentVersionAndEmbeddingIsNullOrderByChunkIndexAsc(
                task.documentId(), task.documentVersion());
    if (chunks.isEmpty()) {
      // 이미 다 임베딩된 문서 — 상태만 정리한다.
      embeddingResultWriter.completeEmbedding(
          task.taskId(), task.documentId(), task.documentVersion());
      return;
    }

    try {
      embedAndSaveInBatches(chunks);
      embeddingResultWriter.completeEmbedding(
          task.taskId(), task.documentId(), task.documentVersion());
    } catch (RuntimeException e) {
      // 이미 저장된 청크는 유지하고, 아직 NULL인 청크만 다음 재시도에서 처리한다.
      log.warn("문서 임베딩 실패 (documentId={})", task.documentId(), e);
      embeddingResultWriter.handleFailure(
          task.taskId(), task.documentId(), task.documentVersion(), describeFailure(e));
    }
  }

  // 배치별 결과를 즉시 저장해, 다음 재시도에서 이미 성공한 청크를 다시 추론하지 않는다.
  private void embedAndSaveInBatches(List<DocumentChunk> chunks) {
    for (int start = 0; start < chunks.size(); start += embedBatchSize) {
      int end = Math.min(start + embedBatchSize, chunks.size());
      List<DocumentChunk> batch = chunks.subList(start, end);
      List<float[]> vectors =
          embeddingService.embedAll(batch.stream().map(DocumentChunk::getContent).toList());
      List<Long> chunkIds = batch.stream().map(DocumentChunk::getId).toList();
      List<String> vectorLiterals =
          vectors.stream().map(embeddingService::toVectorLiteral).toList();
      embeddingResultWriter.saveEmbeddings(chunkIds, vectorLiterals);
    }
  }

  private String describeFailure(RuntimeException exception) {
    String message = exception.getMessage();
    String description =
        exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    return description.length() <= 1000 ? description : description.substring(0, 1000);
  }
}
