package com.test_mcp.tibero_mcp.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Flyway가 코사인 거리 검색(<=>)과 같은 연산자 클래스를 가진 부분 HNSW 인덱스를 생성하는지 검증한다.
@SpringBootTest
@Testcontainers
class VectorIndexMigrationIntegrationTest {

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

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void 코사인_검색용_HNSW_부분_인덱스를_생성한다() {
    String indexDefinition =
        jdbcTemplate.queryForObject(
            """
            SELECT pg_get_indexdef(indexrelid)
            FROM pg_index
            WHERE indexrelid = 'idx_document_chunks_embedding_hnsw_cosine'::regclass
            """,
            String.class);

    String predicate =
        jdbcTemplate.queryForObject(
            """
            SELECT pg_get_expr(indpred, indrelid)
            FROM pg_index
            WHERE indexrelid = 'idx_document_chunks_embedding_hnsw_cosine'::regclass
            """,
            String.class);

    assertThat(indexDefinition)
        .containsIgnoringCase("USING hnsw")
        .contains("embedding vector_cosine_ops");
    assertThat(predicate).isEqualTo("(embedding IS NOT NULL)");
  }
}
