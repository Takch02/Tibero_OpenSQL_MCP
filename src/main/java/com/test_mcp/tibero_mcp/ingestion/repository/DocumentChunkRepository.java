package com.test_mcp.tibero_mcp.ingestion.repository;

import com.test_mcp.tibero_mcp.ingestion.entity.DocumentChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

  List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

  // 아직 임베딩되지 않은 청크(부분 재개 시 이미 채워진 청크는 건너뛴다). embedding=NULL이 곧 미처리 상태다.
  List<DocumentChunk> findByDocumentIdAndDocumentVersionAndEmbeddingIsNullOrderByChunkIndexAsc(
      Long documentId, Integer documentVersion);

  boolean existsByDocumentIdAndDocumentVersionAndEmbeddingIsNull(
      Long documentId, Integer documentVersion);

  long countByDocumentIdAndDocumentVersion(Long documentId, Integer documentVersion);

  long countByDocumentIdAndDocumentVersionAndEmbeddingIsNotNull(
      Long documentId, Integer documentVersion);

  // 정형 데이터(권한 owner_id, 최신 버전 여부, 카테고리)와 벡터 유사도(코사인, <=>)를 한 쿼리로 결합한다.
  // owner_id 필터는 검색 권한 경계이므로 항상 걸고, category는 선택적 메타데이터 필터다.
  // d.version = c.document_version 조건으로 문서가 재업로드되어 버전이 올라가도 옛 버전 청크는 검색되지 않는다.
  @Query(
      value =
          "SELECT c.document_id AS documentId, d.title AS documentTitle, "
              + "c.document_version AS documentVersion, d.category AS category, "
              + "c.chunk_index AS chunkIndex, c.content AS content, "
              + "1 - (c.embedding <=> CAST(:embedding AS vector)) AS score "
              + "FROM document_chunks c "
              + "JOIN documents d ON d.id = c.document_id "
              + "WHERE d.owner_id = :ownerId "
              + "AND d.current_search_version = c.document_version "
              + "AND d.deleted_at IS NULL "
              + "AND (:category IS NULL OR d.category = :category) "
              + "AND c.embedding IS NOT NULL "
              + "ORDER BY c.embedding <=> CAST(:embedding AS vector) "
              + "LIMIT :limit",
      nativeQuery = true)
  List<ChunkSearchProjection> searchByOwnerAndCategory(
      @Param("embedding") String embedding,
      @Param("ownerId") String ownerId,
      @Param("category") String category,
      @Param("limit") int limit);
}
