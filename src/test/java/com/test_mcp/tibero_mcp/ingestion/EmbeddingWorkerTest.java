package com.test_mcp.tibero_mcp.ingestion;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.test_mcp.tibero_mcp.chaos.EmbeddingFailureInjector;
import com.test_mcp.tibero_mcp.embedding.EmbeddingService;
import com.test_mcp.tibero_mcp.exception.IngestionTaskNotFoundException;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmbeddingWorkerTest {

  private final DocumentChunkRepository documentChunkRepository =
      org.mockito.Mockito.mock(DocumentChunkRepository.class);
  private final EmbeddingService embeddingService =
      org.mockito.Mockito.mock(EmbeddingService.class);
  private final EmbeddingFailureInjector embeddingFailureInjector = contents -> {};
  private final EmbeddingResultWriter embeddingResultWriter =
      org.mockito.Mockito.mock(EmbeddingResultWriter.class);
  private final IngestionTaskClaimer ingestionTaskClaimer =
      org.mockito.Mockito.mock(IngestionTaskClaimer.class);
  private final EmbeddingWorker embeddingWorker =
      new EmbeddingWorker(
          documentChunkRepository,
          embeddingService,
          embeddingFailureInjector,
          embeddingResultWriter,
          ingestionTaskClaimer);

  @Test
  void 첫_작업의_lease_갱신이_실패해도_다음_claim_작업을_처리한다() {
    IngestionTaskClaim failedTask = new IngestionTaskClaim(1L, 10L, 1, "worker-1");
    IngestionTaskClaim nextTask = new IngestionTaskClaim(2L, 20L, 1, "worker-1");
    ReflectionTestUtils.setField(embeddingWorker, "batchSize", 2);
    given(ingestionTaskClaimer.claimPendingTasks(2)).willReturn(List.of(failedTask, nextTask));
    given(embeddingResultWriter.renewLease(failedTask.taskId(), failedTask.workerId()))
        .willThrow(new IngestionTaskNotFoundException(failedTask.taskId()));
    given(embeddingResultWriter.renewLease(nextTask.taskId(), nextTask.workerId()))
        .willReturn(true);
    given(
            documentChunkRepository
                .findByDocumentIdAndDocumentVersionAndEmbeddingIsNullOrderByChunkIndexAsc(
                    nextTask.documentId(), nextTask.documentVersion()))
        .willReturn(List.of());
    given(
            embeddingResultWriter.completeEmbedding(
                nextTask.taskId(),
                nextTask.documentId(),
                nextTask.documentVersion(),
                nextTask.workerId()))
        .willReturn(true);

    embeddingWorker.pollAndProcess();

    verify(embeddingResultWriter).renewLease(nextTask.taskId(), nextTask.workerId());
    verify(embeddingResultWriter)
        .completeEmbedding(
            nextTask.taskId(),
            nextTask.documentId(),
            nextTask.documentVersion(),
            nextTask.workerId());
  }
}
