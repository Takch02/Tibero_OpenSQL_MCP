package com.test_mcp.tibero_mcp.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(TiberoMcpException.class)
  public ResponseEntity<ErrorResponse> handleTiberoMcpException(TiberoMcpException e) {
    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(new ErrorResponse(e.getErrorCode().name(), e.getMessage()));
  }
}
