package com.test_mcp.tibero_mcp.chaos;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 기본 프로필에서는 임베딩 실패를 주입하지 않아 운영과 CI 흐름에 영향을 주지 않는다. */
@Component
@Profile("!opensql-smoke")
class NoOpEmbeddingFailureInjector implements EmbeddingFailureInjector {

  @Override
  public void beforeEmbedding(List<String> contents) {
    // 실패 주입은 opensql-smoke 프로필에서만 동작한다.
  }
}
