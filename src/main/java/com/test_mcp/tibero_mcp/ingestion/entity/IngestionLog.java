package com.test_mcp.tibero_mcp.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ingestion_log")
@Getter
@NoArgsConstructor
public class IngestionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "document_version", nullable = false)
  private Integer documentVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IngestionEvent event;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public IngestionLog(
      Long documentId, Integer documentVersion, IngestionEvent event, DocumentStatus status) {
    this.documentId = documentId;
    this.documentVersion = documentVersion;
    this.event = event;
    this.status = status;
    this.createdAt = Instant.now();
  }
}
