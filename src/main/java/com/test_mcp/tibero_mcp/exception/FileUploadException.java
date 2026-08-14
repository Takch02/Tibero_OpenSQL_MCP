package com.test_mcp.tibero_mcp.exception;

// 파일 형식·크기·텍스트 추출 실패를 일관된 400 응답으로 노출하고 파서 예외 원문은 감춘다.
public class FileUploadException extends TiberoMcpException {

  private static final long BYTES_PER_MEBIBYTE = 1024 * 1024;

  public FileUploadException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public FileUploadException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  public static FileUploadException fileSizeLimitExceeded(long maxFileSizeBytes) {
    return new FileUploadException(
        ErrorCode.FILE_SIZE_LIMIT_EXCEEDED, fileSizeLimitMessage(maxFileSizeBytes));
  }

  public static String fileSizeLimitMessage(long maxFileSizeBytes) {
    if (maxFileSizeBytes % BYTES_PER_MEBIBYTE == 0) {
      return "파일 크기는 " + maxFileSizeBytes / BYTES_PER_MEBIBYTE + " MiB 이하여야 합니다.";
    }
    return "파일 크기는 " + maxFileSizeBytes + " B 이하여야 합니다.";
  }
}
