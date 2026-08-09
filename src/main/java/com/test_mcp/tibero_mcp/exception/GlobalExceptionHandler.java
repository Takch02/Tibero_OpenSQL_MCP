package com.test_mcp.tibero_mcp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
  }

  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("DOCUMENT_NOT_FOUND", e.getMessage()));
  }

  @ExceptionHandler(DocumentVersionConflictException.class)
  public ResponseEntity<ErrorResponse> handleDocumentVersionConflict(
      DocumentVersionConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("DOCUMENT_VERSION_CONFLICT", e.getMessage()));
  }

  @ExceptionHandler(EmbeddingDimensionMismatchException.class)
  public ResponseEntity<ErrorResponse> handleEmbeddingDimensionMismatch(
      EmbeddingDimensionMismatchException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("EMBEDDING_DIMENSION_MISMATCH", e.getMessage()));
  }
}
