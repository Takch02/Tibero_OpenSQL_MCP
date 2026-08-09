package com.test_mcp.tibero_mcp.exception;

// 도메인 예외 공통 베이스. GlobalExceptionHandler가 하위 타입별로 HTTP 상태를 매핑한다.
public abstract class TiberoMcpException extends RuntimeException {

  protected TiberoMcpException(String message) {
    super(message);
  }
}
