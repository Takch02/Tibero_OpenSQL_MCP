package com.test_mcp.tibero_mcp.ingestion;

import com.test_mcp.tibero_mcp.embedding.EmbeddingService;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentChunk;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Outbox 소비자. PENDING 문서를 폴링해 청크를 배치 추론하고 결과만 짧은 트랜잭션으로 반영한다.
// 느린 모델 추론을 트랜잭션 밖에 두는 것이 P1(트랜잭션이 오래 열려 failover에 통째로 날아가는 것 방지)의 핵심.
@Component
@RequiredArgsConstructor
public class EmbeddingWorker {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);

  private final DocumentRepository documentRepository;
  private final DocumentChunkRepository documentChunkRepository;
  private final EmbeddingService embeddingService;
  private final EmbeddingResultWriter embeddingResultWriter;

  @Value("${app.embedding.worker.batch-size}")
  private int batchSize;

  // 한 번의 embedAll() 호출에 넣을 청크 수 상한. 수백 페이지 PDF처럼 청크가 매우 많은 문서를
  // 한 번에 통째로 추론시키면 요청이 비대해지고, 실패 시 전체가 롤백되는 위험이 커진다.
  @Value("${app.embedding.worker.embed-batch-size}")
  private int embedBatchSize;

  @Scheduled(
      fixedDelayString = "${app.embedding.worker.poll-interval-ms}",
      initialDelayString = "${app.embedding.worker.initial-delay-ms}")
  public void pollAndProcess() {
    List<Document> pending =
        documentRepository.findByStatusOrderByIdAsc(DocumentStatus.PENDING, Limit.of(batchSize));
    for (Document document : pending) {
      process(document);
    }
  }

  // 문서 하나를 처리한다. 추론(느림)은 여기서 트랜잭션 없이 수행하고, DB 반영만 별도 트랜잭션 빈에 위임한다.
  void process(Document document) {
    List<DocumentChunk> chunks =
        documentChunkRepository
            .findByDocumentIdAndDocumentVersionAndEmbeddingIsNullOrderByChunkIndexAsc(
                document.getId(), document.getVersion());
    if (chunks.isEmpty()) {
      // 이미 다 임베딩된 문서 — 상태만 정리한다.
      embeddingResultWriter.applyEmbeddings(
          document.getId(), document.getVersion(), List.of(), List.of());
      return;
    }

    try {
      List<String> contents = chunks.stream().map(DocumentChunk::getContent).toList();
      List<float[]> vectors = embedInBatches(contents);

      List<Long> chunkIds = chunks.stream().map(DocumentChunk::getId).toList();
      List<String> vectorLiterals =
          vectors.stream().map(embeddingService::toVectorLiteral).toList();

      embeddingResultWriter.applyEmbeddings(
          document.getId(), document.getVersion(), chunkIds, vectorLiterals);
    } catch (RuntimeException e) {
      // 모델 오류 등 — FAILED로 기록하고 이력을 남긴다. embedding=NULL이 유지되므로 재처리 여지가 있다.
      log.warn("문서 임베딩 실패 (documentId={})", document.getId(), e);
      embeddingResultWriter.markFailed(document.getId(), document.getVersion());
    }
  }

  // contents를 embedBatchSize 단위로 나눠 순서를 보존한 채 추론한다.
  private List<float[]> embedInBatches(List<String> contents) {
    List<float[]> vectors = new ArrayList<>(contents.size());
    for (int start = 0; start < contents.size(); start += embedBatchSize) {
      int end = Math.min(start + embedBatchSize, contents.size());
      vectors.addAll(embeddingService.embedAll(contents.subList(start, end)));
    }
    return vectors;
  }
}
