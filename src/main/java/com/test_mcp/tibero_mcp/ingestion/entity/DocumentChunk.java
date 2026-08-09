package com.test_mcp.tibero_mcp.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "document_chunks")
@Getter
@NoArgsConstructor
public class DocumentChunk {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "document_version", nullable = false)
  private Integer documentVersion;

  @Column(name = "chunk_index", nullable = false)
  private Integer chunkIndex;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  // 업로드 시점에는 항상 NULL. 임베딩은 트랜잭션 밖에서 채워진다.
  @Column(columnDefinition = "vector(384)")
  @ColumnTransformer(write = "?::vector")
  private String embedding;

  public DocumentChunk(
      Long documentId, Integer documentVersion, Integer chunkIndex, String content) {
    this.documentId = documentId;
    this.documentVersion = documentVersion;
    this.chunkIndex = chunkIndex;
    this.content = content;
  }
}
