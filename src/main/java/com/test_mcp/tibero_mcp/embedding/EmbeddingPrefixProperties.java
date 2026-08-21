package com.test_mcp.tibero_mcp.embedding;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 임베딩 모델마다 요구하는 검색 역할 접두사를 설정한다.
// 설정이 없는 테스트 프로필도 빈 접두사로 안전하게 동작시킨다.
@ConfigurationProperties(prefix = "app.embedding.prefix")
public record EmbeddingPrefixProperties(String document, String query) {

  public EmbeddingPrefixProperties {
    document = Objects.requireNonNullElse(document, "");
    query = Objects.requireNonNullElse(query, "");
  }
}
