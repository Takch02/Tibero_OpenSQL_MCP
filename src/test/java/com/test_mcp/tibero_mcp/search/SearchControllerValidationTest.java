package com.test_mcp.tibero_mcp.search;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.test_mcp.tibero_mcp.ingestion.repository.ChunkSearchProjection;
import java.util.List;
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

  @Test
  void 검색_결과에_문서_버전을_포함한다() throws Exception {
    ChunkSearchProjection result = mock(ChunkSearchProjection.class);
    given(result.getDocumentId()).willReturn(1L);
    given(result.getDocumentTitle()).willReturn("보안 정책");
    given(result.getDocumentVersion()).willReturn(2);
    given(result.getCategory()).willReturn("security");
    given(result.getChunkIndex()).willReturn(0);
    given(result.getContent()).willReturn("v2 검색 결과");
    given(result.getScore()).willReturn(0.9);
    given(searchService.searchSimilar("보안 정책", "user-1", "security", 5))
        .willReturn(List.of(result));

    mockMvc
        .perform(
            get("/api/search")
                .param("query", "보안 정책")
                .param("ownerId", "user-1")
                .param("category", "security"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].documentId").value(1))
        .andExpect(jsonPath("$[0].documentTitle").value("보안 정책"))
        .andExpect(jsonPath("$[0].documentVersion").value(2))
        .andExpect(jsonPath("$[0].category").value("security"))
        .andExpect(jsonPath("$[0].chunkIndex").value(0));
  }
}
