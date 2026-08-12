package com.test_mcp.tibero_mcp.ingestion;

import com.test_mcp.tibero_mcp.exception.DocumentNotFoundException;
import com.test_mcp.tibero_mcp.exception.IncompleteEmbeddingException;
import com.test_mcp.tibero_mcp.exception.IngestionTaskNotFoundException;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentVersion;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkBatchWriter;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentVersionRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 임베딩 추론 결과를 DB에 반영하는 짧은 트랜잭션. 느린 모델 추론은 이 트랜잭션 밖(EmbeddingWorker)에서
// 끝내고, 여기서는 청크 embedding 배치 UPDATE + 문서 상태 전이 + 로그 기록만 원자적으로 커밋한다.
@Component
@RequiredArgsConstructor
public class EmbeddingResultWriter {

  private final DocumentRepository documentRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final DocumentChunkRepository documentChunkRepository;
  private final DocumentChunkBatchWriter documentChunkBatchWriter;
  private final IngestionLogRepository ingestionLogRepository;
  private final IngestionTaskRepository ingestionTaskRepository;

  @Value("${app.embedding.worker.retry.max-attempts}")
  private int maxAttempts;

  @Value("${app.embedding.worker.retry.initial-backoff-ms}")
  private long initialBackoffMillis;

  @Value("${app.embedding.worker.retry.max-backoff-ms}")
  private long maxBackoffMillis;

  @Transactional
  public void saveEmbeddings(List<Long> chunkIds, List<String> vectorLiterals) {
    documentChunkBatchWriter.updateEmbeddings(chunkIds, vectorLiterals);
  }

  @Transactional
  public void completeEmbedding(Long taskId, Long documentId, Integer documentVersion) {
    if (documentChunkRepository.existsByDocumentIdAndDocumentVersionAndEmbeddingIsNull(
        documentId, documentVersion)) {
      throw new IncompleteEmbeddingException(documentId, documentVersion);
    }

    Document document = findDocument(documentId);
    DocumentVersion version = findDocumentVersion(documentId, documentVersion);
    // version, document, IngestionTask.status 임베딩 성공으로 변경
    version.markEmbedded();
    document.markEmbedded(documentVersion);
    findTask(taskId).markEmbedded();
    ingestionLogRepository.save(
        new IngestionLog(
            documentId, documentVersion, IngestionEvent.EMBEDDED, DocumentStatus.EMBEDDED));
  }

  @Transactional
  public void handleFailure(
      Long taskId, Long documentId, Integer documentVersion, String lastError) {
    Document document = findDocument(documentId);
    DocumentVersion version = findDocumentVersion(documentId, documentVersion);
    IngestionTask task = findTask(taskId);
    if (task.getAttemptCount() < maxAttempts) {
      task.scheduleRetry(Instant.now().plus(calculateBackoff(task.getAttemptCount())), lastError);
      return;
    }

    version.markFailed();
    document.markFailed(documentVersion);
    task.markFailed();
    ingestionLogRepository.save(
        new IngestionLog(
            documentId, documentVersion, IngestionEvent.FAILED, DocumentStatus.FAILED));
  }

  private Duration calculateBackoff(int attemptCount) {
    long multiplier = 1L << Math.min(attemptCount - 1, 30);
    long delay =
        initialBackoffMillis > maxBackoffMillis / multiplier
            ? maxBackoffMillis
            : initialBackoffMillis * multiplier;
    return Duration.ofMillis(delay);
  }

  private IngestionTask findTask(Long taskId) {
    return ingestionTaskRepository
        .findById(taskId)
        .orElseThrow(() -> new IngestionTaskNotFoundException(taskId));
  }

  private Document findDocument(Long documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
  }

  private DocumentVersion findDocumentVersion(Long documentId, Integer documentVersion) {
    return documentVersionRepository
        .findByDocumentIdAndVersion(documentId, documentVersion)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
  }
}
