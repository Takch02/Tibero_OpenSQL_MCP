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
  private final IngestionRetryPolicy ingestionRetryPolicy;

  @Value("${app.embedding.worker.lease.duration-ms}")
  private long leaseDurationMillis;

  @Transactional
  public void saveEmbeddings(List<Long> chunkIds, List<String> vectorLiterals) {
    documentChunkBatchWriter.updateEmbeddings(chunkIds, vectorLiterals);
  }

  @Transactional
  // worker 소유권을 확인하면서 lease를 갱신해 회수기와의 상태 전이를 직렬화한다.
  public boolean renewLease(Long taskId, String workerId) {
    Instant now = Instant.now();
    return findTask(taskId)
        .renewLease(workerId, now, now.plus(Duration.ofMillis(leaseDurationMillis)));
  }

  @Transactional
  // 모든 청크 완료와 lease 소유권을 함께 확인한 경우에만 검색 버전을 새 버전으로 전환한다.
  public boolean completeEmbedding(
      Long taskId, Long documentId, Integer documentVersion, String workerId) {
    Document document = findDocument(documentId);
    DocumentVersion version = findDocumentVersion(documentId, documentVersion);
    IngestionTask task = findTask(taskId);
    if (!task.isClaimedBy(workerId)) {
      return false;
    }
    if (documentChunkRepository.existsByDocumentIdAndDocumentVersionAndEmbeddingIsNull(
        documentId, documentVersion)) {
      throw new IncompleteEmbeddingException(documentId, documentVersion);
    }

    // version, document, IngestionTask.status 임베딩 성공으로 변경
    version.markEmbedded();
    document.markEmbedded(documentVersion);
    task.markEmbedded();
    ingestionLogRepository.save(
        new IngestionLog(
            documentId, documentVersion, IngestionEvent.EMBEDDED, DocumentStatus.EMBEDDED));
    return true;
  }

  @Transactional
  // 옛 워커의 실패 결과가 새 worker가 점유한 작업을 재시도·실패 상태로 바꾸지 못하게 한다.
  public boolean handleFailure(
      Long taskId, Long documentId, Integer documentVersion, String workerId, String lastError) {
    Document document = findDocument(documentId);
    DocumentVersion version = findDocumentVersion(documentId, documentVersion);
    IngestionTask task = findTask(taskId);
    if (!task.isClaimedBy(workerId)) {
      return false;
    }
    if (ingestionRetryPolicy.canRetry(task.getAttemptCount())) {
      task.scheduleRetry(
          ingestionRetryPolicy.nextAttemptAt(task.getAttemptCount(), Instant.now()), lastError);
      return true;
    }

    version.markFailed();
    document.markFailed(documentVersion);
    task.markFailed(lastError);
    ingestionLogRepository.save(
        new IngestionLog(
            documentId, documentVersion, IngestionEvent.FAILED, DocumentStatus.FAILED));
    return true;
  }

  private IngestionTask findTask(Long taskId) {
    return ingestionTaskRepository
        .findByIdForUpdate(taskId)
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
