package com.test_mcp.tibero_mcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// MCP 서버가 도구를 실제로 발견하려면 ToolCallbackProvider 빈이 search_documents를 노출해야 한다.
// 전체 MCP 프로토콜 핸드셰이크(streamable HTTP)까지 검증하는 대신, 도구 등록 자체를 확인한다.
@SpringBootTest
@Testcontainers
class McpToolConfigIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired ToolCallbackProvider searchToolCallbacks;

  @Test
  void search_documents_도구가_등록된다() {
    ToolCallback[] callbacks = searchToolCallbacks.getToolCallbacks();

    assertThat(callbacks)
        .extracting(callback -> callback.getToolDefinition().name())
        .contains("search_documents");
  }
}
