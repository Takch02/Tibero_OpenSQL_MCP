package com.test_mcp.tibero_mcp.mcp;

import com.test_mcp.tibero_mcp.search.SearchService;
import com.test_mcp.tibero_mcp.search.dto.SearchResultResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

// MCP 클라이언트(예: Claude Desktop)에 노출되는 검색 도구. SearchService를 그대로 감싸며,
// 이 프로젝트의 유일한 외부 노출 지점은 MCP 규격이라는 과제 목표에 맞춰 REST와는 별도로 유지한다.
@Component
@RequiredArgsConstructor
public class SearchMcpTools {

  private final SearchService searchService;

  @Tool(
      name = "search_documents",
      description =
          "업로드된 문서에서 질의와 의미적으로 유사한 청크를 검색한다. ownerId가 소유한 문서만 검색되며(권한 필터),"
              + " category로 결과를 좁힐 수 있다.")
  public List<SearchResultResponse> searchDocuments(
      @ToolParam(description = "검색 질의") String query,
      @ToolParam(description = "검색을 요청하는 사용자 ID. 이 사용자가 소유한 문서만 검색된다.") String ownerId,
      @ToolParam(description = "카테고리 필터(선택)", required = false) String category,
      @ToolParam(description = "반환할 최대 청크 수") int limit) {
    return searchService.searchSimilar(query, ownerId, category, limit).stream()
        .map(SearchResultResponse::from)
        .toList();
  }
}
