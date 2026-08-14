package com.test_mcp.tibero_mcp.exception;

// 파일 형식·크기·텍스트 추출 실패를 일관된 400 응답으로 노출하고 파서 예외 원문은 감춘다.
public class FileUploadException extends TiberoMcpException {

  public FileUploadException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
