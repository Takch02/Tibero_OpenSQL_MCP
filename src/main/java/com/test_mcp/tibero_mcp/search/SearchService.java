package com.test_mcp.tibero_mcp.search;

import com.test_mcp.tibero_mcp.embedding.EmbeddingService;
import com.test_mcp.tibero_mcp.ingestion.repository.ChunkSearchProjection;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchService {

  private final DocumentChunkRepository documentChunkRepository;
  private final EmbeddingService embeddingService;

  // ownerId는 검색 권한 경계라 필수. category는 선택적 메타데이터 필터(null이면 전체 카테고리 대상).
  // 정형 조건(권한/버전/카테고리)과 벡터 유사도(코사인)를 SearchService가 아니라 DB 쿼리 한 방에서 결합한다.
  @Transactional(readOnly = true)
  public List<ChunkSearchProjection> searchSimilar(
      String query, String ownerId, String category, int limit) {
    float[] queryEmbedding = embeddingService.embed(query);
    return documentChunkRepository.searchByOwnerAndCategory(
        embeddingService.toVectorLiteral(queryEmbedding), ownerId, category, limit);
  }
}
