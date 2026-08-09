package com.test_mcp.tibero_mcp.search;

import com.test_mcp.tibero_mcp.exception.InvalidRequestException;
import com.test_mcp.tibero_mcp.search.dto.SearchResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "벡터 유사도 검색 API")
public class SearchController {

  private final SearchService searchService;

  @GetMapping
  @Operation(
      summary = "유사 문서 청크 검색",
      description =
          "질의를 임베딩해 pgvector 코사인 유사도로 검색한다. ownerId(권한)·category(메타데이터)·문서 최신 버전 여부를"
              + " 정형 조건으로 함께 걸러 벡터 검색과 한 쿼리로 결합한다.")
  @ApiResponse(responseCode = "200", description = "검색 성공")
  public List<SearchResultResponse> search(
      @RequestParam String query,
      @RequestParam String ownerId,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "5") int limit) {
    if (!StringUtils.hasText(query) || !StringUtils.hasText(ownerId)) {
      throw new InvalidRequestException("query/ownerId는 필수입니다.");
    }
    return searchService.searchSimilar(query, ownerId, category, limit).stream()
        .map(SearchResultResponse::from)
        .toList();
  }
}
