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

  @Column(name = "source_filename")
  private String sourceFilename;

  @Column(name = "source_content_type")
  private String sourceContentType;

  @Column(name = "source_size_bytes")
  private Long sourceSizeBytes;

  @Column(name = "source_file_hash")
  private String sourceFileHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  // 새 버전은 임베딩 전이므로 항상 PENDING 상태에서 생성한다.
  public static DocumentVersion pending(
      Long documentId,
      Integer version,
      String contentHash,
      String content,
      String createdBy,
      SourceFileMetadata sourceFileMetadata) {
    return new DocumentVersion(
        documentId, version, contentHash, content, createdBy, sourceFileMetadata);
  }

  private DocumentVersion(
      Long documentId,
      Integer version,
      String contentHash,
      String content,
      String createdBy,
      SourceFileMetadata sourceFileMetadata) {
    this.documentId = documentId;
    this.version = version;
    this.contentHash = contentHash;
    this.content = content;
    this.status = DocumentStatus.PENDING;
    this.createdBy = createdBy;
    this.sourceFilename = sourceFileMetadata == null ? null : sourceFileMetadata.filename();
    this.sourceContentType = sourceFileMetadata == null ? null : sourceFileMetadata.contentType();
    this.sourceSizeBytes = sourceFileMetadata == null ? null : sourceFileMetadata.sizeBytes();
    this.sourceFileHash = sourceFileMetadata == null ? null : sourceFileMetadata.fileHash();
    this.createdAt = Instant.now();
  }

  // 복원 시 파일 입력으로 생성된 과거 버전의 출처 정보도 함께 계승한다.
  public SourceFileMetadata sourceFileMetadata() {
    if (sourceFilename == null) {
      return null;
    }
    return new SourceFileMetadata(
        sourceFilename, sourceContentType, sourceSizeBytes, sourceFileHash);
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
