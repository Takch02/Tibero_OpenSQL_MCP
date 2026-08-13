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
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "content_hash", nullable = false)
  private String contentHash;

  private String title;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Column(nullable = false)
  private Integer version;

  @Column(name = "current_search_version")
  private Integer currentSearchVersion;

  // 검색 시 반드시 이 값으로 필터링해 접근 권한이 없는 문서를 걸러낸다.
  @Column(name = "owner_id", nullable = false)
  private String ownerId;

  private String category;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Document(
      String idempotencyKey,
      String contentHash,
      String title,
      String content,
      String ownerId,
      String category) {
    this.idempotencyKey = idempotencyKey;
    this.contentHash = contentHash;
    this.title = title;
    this.content = content;
    this.status = DocumentStatus.PENDING;
    this.version = 1;
    this.ownerId = ownerId;
    this.category = category;
    this.createdAt = Instant.now();
  }

  public void markEmbedded(Integer embeddedVersion) {
    if (this.version == embeddedVersion) {
      this.status = DocumentStatus.EMBEDDED;
      this.currentSearchVersion = embeddedVersion;
    }
  }

  public void markFailed(Integer failedVersion) {
    if (Objects.equals(this.version, failedVersion)) {
      this.status = DocumentStatus.FAILED;
    }
  }

  // 재처리 중에도 마지막 정상 검색 버전은 유지하고 최신 버전의 처리 상태만 PENDING으로 되돌린다.
  public void markPending(Integer pendingVersion) {
    if (Objects.equals(this.version, pendingVersion)) {
      this.status = DocumentStatus.PENDING;
    }
  }

  public void update(String title, String content, String contentHash, String category) {
    this.title = title;
    this.content = content;
    this.contentHash = contentHash;
    this.category = category;
    this.version++;
    this.status = DocumentStatus.PENDING;
  }

  public void markDeleted() {
    this.status = DocumentStatus.DELETED;
    this.deletedAt = Instant.now();
  }

  public void restore(String content, String contentHash) {
    this.content = content;
    this.contentHash = contentHash;
    this.version++;
    this.status = DocumentStatus.PENDING;
    this.deletedAt = null;
  }
}
