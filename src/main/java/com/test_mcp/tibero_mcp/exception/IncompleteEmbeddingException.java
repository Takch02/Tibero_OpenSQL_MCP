package com.test_mcp.tibero_mcp.exception;

public class IncompleteEmbeddingException extends TiberoMcpException {

  public IncompleteEmbeddingException(Long documentId, Integer documentVersion) {
    super(
        ErrorCode.INCOMPLETE_EMBEDDING,
        "미임베딩 청크가 남아 있어 검색 버전을 전환할 수 없습니다. documentId="
            + documentId
            + ", documentVersion="
            + documentVersion);
  }
}
