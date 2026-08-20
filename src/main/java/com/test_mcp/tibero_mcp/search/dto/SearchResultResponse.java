package com.test_mcp.tibero_mcp.search.dto;

import com.test_mcp.tibero_mcp.ingestion.repository.ChunkSearchProjection;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검색된 문서 청크")
public record SearchResultResponse(
    @Schema(description = "원본 문서 ID") Long documentId,
    @Schema(description = "검색 결과의 현재 문서 제목") String documentTitle,
    @Schema(description = "검색에 사용된 문서 버전") Integer documentVersion,
    @Schema(description = "문서 카테고리") String category,
    @Schema(description = "문서 내 청크 순서") Integer chunkIndex,
    @Schema(description = "청크 본문") String content,
    @Schema(description = "코사인 유사도 점수(1에 가까울수록 유사)") Double score) {

  public static SearchResultResponse from(ChunkSearchProjection projection) {
    return new SearchResultResponse(
        projection.getDocumentId(),
        projection.getDocumentTitle(),
        projection.getDocumentVersion(),
        projection.getCategory(),
        projection.getChunkIndex(),
        projection.getContent(),
        projection.getScore());
  }
}
