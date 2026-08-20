package com.test_mcp.tibero_mcp.ingestion.repository;

// documents(정형 데이터: 권한, 카테고리)와 document_chunks(벡터)를 한 쿼리로 join한 결과 프로젝션.
public interface ChunkSearchProjection {

  Long getDocumentId();

  String getDocumentTitle();

  Integer getDocumentVersion();

  String getCategory();

  Integer getChunkIndex();

  String getContent();

  Double getScore();
}
