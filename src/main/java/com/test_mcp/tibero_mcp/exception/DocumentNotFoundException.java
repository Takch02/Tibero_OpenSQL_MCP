package com.test_mcp.tibero_mcp.exception;

// 임베딩 워커가 결과를 반영하려는 시점에 문서가 이미 사라진 비정상 상태. GlobalExceptionHandler가 404로 매핑한다.
public class DocumentNotFoundException extends TiberoMcpException {

  public DocumentNotFoundException(Long documentId) {
    super("문서를 찾을 수 없습니다. documentId=" + documentId);
  }
}
