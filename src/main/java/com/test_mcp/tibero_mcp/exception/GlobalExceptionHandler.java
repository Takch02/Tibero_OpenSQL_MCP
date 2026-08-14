package com.test_mcp.tibero_mcp.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(TiberoMcpException.class)
  public ResponseEntity<ErrorResponse> handleTiberoMcpException(TiberoMcpException e) {
    return ResponseEntity.status(e.getErrorCode().getHttpStatus())
        .body(new ErrorResponse(e.getErrorCode().name(), e.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException e) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                ErrorCode.FILE_SIZE_LIMIT_EXCEEDED.name(), "파일 크기는 10 MiB 이하여야 합니다."));
  }
}
