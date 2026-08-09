package com.test_mcp.tibero_mcp.ingestion.repository;

import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionLogRepository extends JpaRepository<IngestionLog, Long> {

  List<IngestionLog> findByDocumentId(Long documentId);
}
