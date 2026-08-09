package com.test_mcp.tibero_mcp.ingestion;

import com.test_mcp.tibero_mcp.exception.DocumentNotFoundException;
import com.test_mcp.tibero_mcp.exception.DocumentVersionConflictException;
import com.test_mcp.tibero_mcp.exception.InvalidRequestException;
import com.test_mcp.tibero_mcp.ingestion.chunking.Chunker;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentVersion;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkBatchWriter;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentVersionRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngestionService {

  private final DocumentRepository documentRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final DocumentChunkBatchWriter documentChunkBatchWriter;
  private final IngestionLogRepository ingestionLogRepository;
  private final Chunker chunker;

  // 임베딩(모델 추론)은 여기서 하지 않는다. 느린 작업을 트랜잭션 안에 넣으면 트랜잭션이 오래 열려
  // 있다가 failover에 통째로 날아갈 수 있어, 청크만 embedding=NULL로 저장하고 처리 예약(ingestion_log)만 남긴다.
  // 실제 임베딩은 이 트랜잭션 밖에서 별도로 처리한다(Outbox 패턴).
  @Transactional
  public Document upload(
      String idempotencyKey, String title, String content, String ownerId, String category) {
    String contentHash = sha256(content);

    // 같은 요청 재시도만 멱등하게 처리한다. 내용 해시를 전역 중복 기준으로 쓰면 서로 다른 소유자의
    // 같은 본문이 같은 문서로 합쳐져 권한 경계가 깨질 수 있다.
    Optional<Document> existing = documentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return existing.get();
    }

    Document document =
        documentRepository.save(
            new Document(idempotencyKey, contentHash, title, content, ownerId, category));

    documentVersionRepository.save(
        new DocumentVersion(
            document.getId(),
            document.getVersion(),
            contentHash,
            content,
            DocumentStatus.PENDING,
            ownerId));

    // batch update로 1번 insert
    List<String> chunks = chunker.chunk(content);
    documentChunkBatchWriter.insertAll(document.getId(), document.getVersion(), chunks);

    ingestionLogRepository.save(
        new IngestionLog(
            document.getId(),
            document.getVersion(),
            IngestionEvent.CREATED,
            DocumentStatus.PENDING));

    return document;
  }

  @Transactional(readOnly = true)
  public Document getDocument(Long documentId, String ownerId) {
    return documentRepository
        .findByIdAndOwnerIdAndDeletedAtIsNull(documentId, ownerId)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
  }

  @Transactional(readOnly = true)
  public List<DocumentVersion> getVersions(Long documentId, String ownerId) {
    documentRepository
        .findByIdAndOwnerId(documentId, ownerId)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
    return documentVersionRepository.findByDocumentIdOrderByVersionDesc(documentId);
  }

  @Transactional
  public Document update(
      Long documentId,
      String ownerId,
      Integer expectedVersion,
      String title,
      String content,
      String category) {
    Document document = findLockedDocument(documentId, ownerId);
    validateExpectedVersion(document, expectedVersion);

    String contentHash = sha256(content);
    if (document.getContentHash().equals(contentHash)) {
      return document;
    }

    // 버전 UP
    document.update(title, content, contentHash, category);
    documentVersionRepository.save(
        new DocumentVersion(
            document.getId(),
            document.getVersion(),
            contentHash,
            content,
            DocumentStatus.PENDING,
            ownerId));

    // 임베딩 시작
    documentChunkBatchWriter.insertAll(
        document.getId(), document.getVersion(), chunker.chunk(content));

    // 실행 내역 저장
    ingestionLogRepository.save(
        new IngestionLog(
            document.getId(),
            document.getVersion(),
            IngestionEvent.UPDATED,
            DocumentStatus.PENDING));
    return document;
  }

  @Transactional
  public void delete(Long documentId, String ownerId, Integer expectedVersion) {
    Document document = findLockedDocument(documentId, ownerId);
    validateExpectedVersion(document, expectedVersion);
    document.markDeleted();
    ingestionLogRepository.save(
        new IngestionLog(
            document.getId(),
            document.getVersion(),
            IngestionEvent.DELETED,
            DocumentStatus.DELETED));
  }

  @Transactional
  public Document restore(
      Long documentId, String ownerId, Integer expectedVersion, Integer sourceVersion) {
    Document document = findLockedDocument(documentId, ownerId);
    validateExpectedVersion(document, expectedVersion);
    if (document.getDeletedAt() == null) {
      throw new InvalidRequestException("삭제된 문서만 복원할 수 있습니다.");
    }

    DocumentVersion source =
        documentVersionRepository
            .findByDocumentIdAndVersion(documentId, sourceVersion)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
    document.restore(source.getContent(), source.getContentHash());
    documentVersionRepository.save(
        new DocumentVersion(
            document.getId(),
            document.getVersion(),
            source.getContentHash(),
            source.getContent(),
            DocumentStatus.PENDING,
            ownerId));
    documentChunkBatchWriter.insertAll(
        document.getId(), document.getVersion(), chunker.chunk(source.getContent()));
    ingestionLogRepository.save(
        new IngestionLog(
            document.getId(),
            document.getVersion(),
            IngestionEvent.RESTORED,
            DocumentStatus.PENDING));
    return document;
  }

  // 비관락 이용
  private Document findLockedDocument(Long documentId, String ownerId) {
    return documentRepository
        .findLockedByIdAndOwnerId(documentId, ownerId)
        .filter(document -> document.getDeletedAt() == null)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
  }

  private static void validateExpectedVersion(Document document, Integer expectedVersion) {
    if (!document.getVersion().equals(expectedVersion)) {
      throw new DocumentVersionConflictException(
          document.getId(), expectedVersion, document.getVersion());
    }
  }

  private static String sha256(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
    }
  }
}
