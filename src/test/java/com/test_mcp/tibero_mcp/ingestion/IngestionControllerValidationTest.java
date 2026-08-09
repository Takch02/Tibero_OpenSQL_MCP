package com.test_mcp.tibero_mcp.ingestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 요청 검증(400) 경로는 DB 접근 전에 걸러지므로 Testcontainers 없이 컨트롤러 계층만 슬라이스로 검증한다.
// 실제 업로드/멱등성 동작은 IngestionControllerIntegrationTest(Testcontainers)에서 검증한다.
@WebMvcTest(IngestionController.class)
class IngestionControllerValidationTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean IngestionService ingestionService;

  @Test
  void content가_비어있으면_400과_에러_코드를_반환한다() throws Exception {
    String body =
        """
        {"idempotencyKey":"key","title":"제목","content":"","ownerId":"user-1"}
        """;

    mockMvc
        .perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void ownerId가_비어있으면_400과_에러_코드를_반환한다() throws Exception {
    String body =
        """
        {"idempotencyKey":"key","title":"제목","content":"본문","ownerId":""}
        """;

    mockMvc
        .perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void idempotencyKey가_비어있으면_400과_에러_코드를_반환한다() throws Exception {
    String body =
        """
        {"idempotencyKey":"","title":"제목","content":"본문","ownerId":"user-1"}
        """;

    mockMvc
        .perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}
