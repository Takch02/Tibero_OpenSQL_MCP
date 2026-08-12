package com.test_mcp.tibero_mcp.exception;

public class DocumentVersionConflictException extends TiberoMcpException {

  public DocumentVersionConflictException(
      Long documentId, Integer expectedVersion, Integer actualVersion) {
    super(
        ErrorCode.DOCUMENT_VERSION_CONFLICT,
        "문서 버전이 일치하지 않습니다. documentId="
            + documentId
            + ", expectedVersion="
            + expectedVersion
            + ", actualVersion="
            + actualVersion);
  }
}
