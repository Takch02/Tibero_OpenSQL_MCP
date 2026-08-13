package com.test_mcp.tibero_mcp.ingestion.repository;

import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestionTaskRepository extends JpaRepository<IngestionTask, Long> {

  Optional<IngestionTask> findByDocumentIdAndDocumentVersion(
      Long documentId, Integer documentVersion);

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
}
