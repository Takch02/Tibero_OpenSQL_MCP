package com.test_mcp.tibero_mcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.test_mcp.tibero_mcp.exception.InvalidRequestException;
import com.test_mcp.tibero_mcp.ingestion.repository.ChunkSearchProjection;
import com.test_mcp.tibero_mcp.search.SearchService;
import com.test_mcp.tibero_mcp.search.dto.SearchResultResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchMcpToolsTest {

  private final SearchService searchService = mock(SearchService.class);
  private final SearchMcpTools searchMcpTools = new SearchMcpTools(searchService);

  @Test
  void 검색_근거와_실제_검색_버전을_MCP_결과로_반환한다() {
    ChunkSearchProjection projection = mock(ChunkSearchProjection.class);
    given(projection.getDocumentId()).willReturn(1L);
    given(projection.getDocumentTitle()).willReturn("관리자 보안 정책");
    given(projection.getDocumentVersion()).willReturn(2);
    given(projection.getCategory()).willReturn("security");
    given(projection.getChunkIndex()).willReturn(3);
    given(projection.getContent()).willReturn("관리자 계정은 다중 인증을 사용해야 합니다.");
    given(projection.getScore()).willReturn(0.91);
    given(searchService.searchSimilar("관리자 보안", "team-a", "security", 5))
        .willReturn(List.of(projection));

    List<SearchResultResponse> result =
        searchMcpTools.searchDocuments("관리자 보안", "team-a", "security", 5);

    assertThat(result)
        .containsExactly(
            new SearchResultResponse(
                1L, "관리자 보안 정책", 2, "security", 3, "관리자 계정은 다중 인증을 사용해야 합니다.", 0.91));
  }

  @Test
  void query가_비어있으면_검색을_호출하지_않는다() {
    assertThatThrownBy(() -> searchMcpTools.searchDocuments("", "team-a", null, 5))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("query/ownerId는 필수입니다.");

    verifyNoInteractions(searchService);
  }

  @Test
  void ownerId가_비어있으면_검색을_호출하지_않는다() {
    assertThatThrownBy(() -> searchMcpTools.searchDocuments("관리자 보안", " ", null, 5))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("query/ownerId는 필수입니다.");

    verifyNoInteractions(searchService);
  }
}
