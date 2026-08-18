package com.test_mcp.tibero_mcp.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

  @Test
  void multipart_요청이_거절돼도_설정된_파일_크기_상한을_안내한다() {
    long maxFileSizeBytes = 3 * 1024 * 1024;
    GlobalExceptionHandler handler = new GlobalExceptionHandler(maxFileSizeBytes);

    var response =
        handler.handleMaxUploadSizeExceededException(
            new MaxUploadSizeExceededException(maxFileSizeBytes));

    assertThat(response.getBody())
        .isEqualTo(
            new ErrorResponse(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED.name(), "파일 크기는 3 MiB 이하여야 합니다."));
  }
}
