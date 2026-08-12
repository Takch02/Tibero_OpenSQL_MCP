package com.test_mcp.tibero_mcp.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST),
  DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
  DOCUMENT_VERSION_CONFLICT(HttpStatus.CONFLICT),
  INGESTION_TASK_NOT_FOUND(HttpStatus.NOT_FOUND),
  INCOMPLETE_EMBEDDING(HttpStatus.CONFLICT),
  EMBEDDING_DIMENSION_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus httpStatus;

  ErrorCode(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
