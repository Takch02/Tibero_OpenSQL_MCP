package com.test_mcp.tibero_mcp.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 임베딩 모델마다 요구하는 검색 역할 접두사를 설정한다.
// 기본 프로필은 빈 문자열을 사용해 기존 all-MiniLM-L6-v2 호출 결과를 유지한다.
@ConfigurationProperties(prefix = "app.embedding.prefix")
public record EmbeddingPrefixProperties(String document, String query) {}
