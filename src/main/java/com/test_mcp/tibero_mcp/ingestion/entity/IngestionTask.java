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
@Table(name = "ingestion_tasks")
@Getter
@NoArgsConstructor
public class IngestionTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "document_version", nullable = false)
  private Integer documentVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IngestionTaskStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  public IngestionTask(Long documentId, Integer documentVersion) {
    this.documentId = documentId;
    this.documentVersion = documentVersion;
    this.status = IngestionTaskStatus.PENDING;
    this.createdAt = Instant.now();
    this.nextAttemptAt = this.createdAt;
  }

  public void markProcessing() {
    this.status = IngestionTaskStatus.PROCESSING;
    this.startedAt = Instant.now();
    this.attemptCount++;
  }

  public void markEmbedded() {
    this.status = IngestionTaskStatus.EMBEDDED;
    this.lastError = null;
  }

  public void markFailed(String lastError) {
    this.status = IngestionTaskStatus.FAILED;
    this.lastError = lastError;
  }

  public void scheduleRetry(Instant nextAttemptAt, String lastError) {
    this.status = IngestionTaskStatus.PENDING;
    this.nextAttemptAt = nextAttemptAt;
    this.lastError = lastError;
    this.startedAt = null;
  }
}
