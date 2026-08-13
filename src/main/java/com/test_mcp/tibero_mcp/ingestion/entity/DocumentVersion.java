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
@Table(name = "document_versions")
@Getter
@NoArgsConstructor
public class DocumentVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(nullable = false)
  private Integer version;

  @Column(name = "content_hash", nullable = false)
  private String contentHash;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Column(name = "created_by", nullable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public DocumentVersion(
      Long documentId,
      Integer version,
      String contentHash,
      String content,
      DocumentStatus status,
      String createdBy) {
    this.documentId = documentId;
    this.version = version;
    this.contentHash = contentHash;
    this.content = content;
    this.status = status;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  public void markEmbedded() {
    this.status = DocumentStatus.EMBEDDED;
  }

  public void markFailed() {
    this.status = DocumentStatus.FAILED;
  }

  // 과거 버전은 수정하지 않고, 최신 FAILED 버전에 한해서만 수동 재처리 상태로 전이한다.
  public void markPending() {
    this.status = DocumentStatus.PENDING;
  }
}
