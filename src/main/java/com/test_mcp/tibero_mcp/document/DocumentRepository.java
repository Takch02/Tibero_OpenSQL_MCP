package com.test_mcp.tibero_mcp.document;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

  // <-> : L2(유클리드) 거리 기준 오름차순 정렬 → 가장 가까운 문서 순
  @Query(
      value =
          "SELECT * FROM document ORDER BY embedding <-> CAST(:embedding AS vector) LIMIT :limit",
      nativeQuery = true)
  List<Document> findNearestNeighbors(
      @Param("embedding") String embedding, @Param("limit") int limit);
}
