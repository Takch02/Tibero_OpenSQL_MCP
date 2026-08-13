package com.test_mcp.tibero_mcp.exception;

import lombok.Getter;

// 도메인 예외 공통 베이스. GlobalExceptionHandler가 ErrorCode의 HTTP 상태를 사용한다.
@Getter
public abstract class TiberoMcpException extends RuntimeException {

  private final ErrorCode errorCode;

  protected TiberoMcpException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
