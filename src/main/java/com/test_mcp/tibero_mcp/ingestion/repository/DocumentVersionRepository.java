package com.test_mcp.tibero_mcp.ingestion.repository;

import com.test_mcp.tibero_mcp.ingestion.entity.DocumentVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

  List<DocumentVersion> findByDocumentIdOrderByVersionDesc(Long documentId);

  Optional<DocumentVersion> findByDocumentIdAndVersion(Long documentId, Integer version);
}
