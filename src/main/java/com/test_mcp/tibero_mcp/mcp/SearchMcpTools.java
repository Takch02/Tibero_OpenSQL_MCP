package com.test_mcp.tibero_mcp.mcp;

import com.test_mcp.tibero_mcp.exception.InvalidRequestException;
import com.test_mcp.tibero_mcp.search.SearchService;
import com.test_mcp.tibero_mcp.search.dto.SearchResultResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// MCP 클라이언트가 문서 근거와 실제 검색 버전을 인용할 수 있도록 SearchService 결과를 그대로 노출한다.
// REST와 동일한 Service를 사용해 owner/category/검색 버전 조건이 두 인터페이스에서 달라지지 않게 한다.
@Component
@RequiredArgsConstructor
public class SearchMcpTools {

  private final SearchService searchService;

  @Tool(
      name = "search_documents",
      description =
          "업로드된 문서에서 질의와 의미적으로 유사한 청크를 검색한다. ownerId 범위의 문서만 검색되며," + " category로 결과를 좁힐 수 있다.")
  public List<SearchResultResponse> searchDocuments(
      @ToolParam(description = "검색 질의") String query,
      @ToolParam(description = "1차 평가용 문서 소유 범위 식별자") String ownerId,
      @ToolParam(description = "카테고리 필터(선택)", required = false) String category,
      @ToolParam(description = "반환할 최대 청크 수") int limit) {
    if (!StringUtils.hasText(query) || !StringUtils.hasText(ownerId)) {
      throw new InvalidRequestException("query/ownerId는 필수입니다.");
    }

    return searchService.searchSimilar(query, ownerId, category, limit).stream()
        .map(SearchResultResponse::from)
        .toList();
  }
}
