package com.test_mcp.tibero_mcp.exception;

// Controller/MCP 도구 요청 검증 실패(필수값 누락 등). GlobalExceptionHandler가 400으로 매핑한다.
public class InvalidRequestException extends TiberoMcpException {

  public InvalidRequestException(String message) {
    super(ErrorCode.INVALID_REQUEST, message);
  }
}
