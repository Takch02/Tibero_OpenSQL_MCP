package com.test_mcp.tibero_mcp.ingestion.repository;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DocumentRepository extends JpaRepository<Document, Long> {

  // 임베딩 워커가 처리할 대기 문서(Outbox). 한 번의 폴링에서 너무 많이 잡지 않도록 Limit로 제한한다.
  List<Document> findByStatusOrderByIdAsc(DocumentStatus status, Limit limit);

  Optional<Document> findByIdempotencyKey(String idempotencyKey);

  Optional<Document> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, String ownerId);

  Optional<Document> findByIdAndOwnerId(Long id, String ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Document> findLockedByIdAndOwnerId(Long id, String ownerId);
}
