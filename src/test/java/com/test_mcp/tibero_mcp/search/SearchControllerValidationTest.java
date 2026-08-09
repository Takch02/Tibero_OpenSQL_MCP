package com.test_mcp.tibero_mcp.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 요청 검증(400) 경로는 DB 접근 전에 걸러지므로 Testcontainers 없이 컨트롤러 계층만 슬라이스로 검증한다.
// 실제 검색 동작은 SearchControllerIntegrationTest(Testcontainers)에서 검증한다.
@WebMvcTest(SearchController.class)
class SearchControllerValidationTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean SearchService searchService;

  @Test
  void query가_비어있으면_400과_에러_코드를_반환한다() throws Exception {
    mockMvc
        .perform(get("/api/search").param("query", "").param("ownerId", "user-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void ownerId가_비어있으면_400과_에러_코드를_반환한다() throws Exception {
    mockMvc
        .perform(get("/api/search").param("query", "pets").param("ownerId", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}
