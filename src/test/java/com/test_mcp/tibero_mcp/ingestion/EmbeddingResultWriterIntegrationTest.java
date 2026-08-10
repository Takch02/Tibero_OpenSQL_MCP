package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.test_mcp.tibero_mcp.exception.DocumentNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class EmbeddingResultWriterIntegrationTest {

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

  @Autowired EmbeddingResultWriter embeddingResultWriter;

  @Test
  void 존재하지_않는_documentId면_DocumentNotFoundException을_던진다() {
    Long missingId = -1L;

    assertThatThrownBy(
            () -> embeddingResultWriter.applyEmbeddings(-1L, missingId, 1, List.of(), List.of()))
        .isInstanceOf(DocumentNotFoundException.class)
        .hasMessageContaining(String.valueOf(missingId));

    assertThatThrownBy(() -> embeddingResultWriter.markFailed(-1L, missingId, 1))
        .isInstanceOf(DocumentNotFoundException.class);
  }
}
