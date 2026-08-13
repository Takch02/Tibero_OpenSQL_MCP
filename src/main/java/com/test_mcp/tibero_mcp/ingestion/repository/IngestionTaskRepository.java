package com.test_mcp.tibero_mcp.ingestion.repository;

import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestionTaskRepository extends JpaRepository<IngestionTask, Long> {

  Optional<IngestionTask> findByDocumentIdAndDocumentVersion(
      Long documentId, Integer documentVersion);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM IngestionTask t WHERE t.id = :taskId")
  // heartbeat·완료·실패와 만료 회수기가 같은 작업의 상태를 동시에 덮어쓰지 않게 한다.
  Optional<IngestionTask> findByIdForUpdate(@Param("taskId") Long taskId);

  @Query(
      value =
          """
          SELECT t.*
          FROM ingestion_tasks t
          JOIN documents d ON d.id = t.document_id
          WHERE t.status = 'PENDING'
            AND t.next_attempt_at <= CURRENT_TIMESTAMP
            AND d.deleted_at IS NULL
            AND d.version = t.document_version
          ORDER BY t.id
          LIMIT :batchSize
          FOR UPDATE OF t SKIP LOCKED
          """,
      nativeQuery = true)
  List<IngestionTask> findPendingForUpdateSkipLocked(@Param("batchSize") int batchSize);

  @Query(
      value =
          """
          SELECT t.*
          FROM ingestion_tasks t
          WHERE t.status = 'PROCESSING'
            AND t.lease_expires_at <= CURRENT_TIMESTAMP
          ORDER BY t.lease_expires_at, t.id
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  // 만료된 PROCESSING 행만 원자적으로 잠가, 여러 회수기가 같은 작업을 중복 회수하지 않게 한다.
  List<IngestionTask> findExpiredProcessingForUpdateSkipLocked(@Param("batchSize") int batchSize);
}
