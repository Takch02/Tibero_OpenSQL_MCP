package com.test_mcp.tibero_mcp.chaos;

import java.util.List;

/** Smoke 환경에서만 실제 워커 실패 경로를 재현하기 위한 임베딩 직전 훅이다. */
public interface EmbeddingFailureInjector {

  void beforeEmbedding(List<String> contents);
}
