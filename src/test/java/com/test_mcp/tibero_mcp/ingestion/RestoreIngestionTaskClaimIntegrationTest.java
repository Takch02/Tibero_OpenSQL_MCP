package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
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
class RestoreIngestionTaskClaimIntegrationTest {

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

  @Autowired IngestionService ingestionService;

  @Autowired IngestionTaskClaimer ingestionTaskClaimer;

  @Autowired IngestionTaskRepository ingestionTaskRepository;

  @Test
  void 복원_후_커밋된_작업은_워커가_claim한다() {
    Document uploaded =
        ingestionService.upload("restore-claim-key", "정책", "첫 번째 정책", "user-1", null);
    Document updated =
        ingestionService.update(uploaded.getId(), "user-1", 1, "정책", "두 번째 정책", null);
    ingestionService.delete(updated.getId(), "user-1", 2);

    Document restored = ingestionService.restore(updated.getId(), "user-1", 2, 1);

    assertThat(
            ingestionTaskRepository
                .findByDocumentIdAndDocumentVersion(restored.getId(), restored.getVersion())
                .orElseThrow()
                .getStatus())
        .isEqualTo(IngestionTaskStatus.PENDING);

    assertThat(ingestionTaskClaimer.claimPendingTasks(1))
        .singleElement()
        .satisfies(
            claim -> {
              assertThat(claim.documentId()).isEqualTo(restored.getId());
              assertThat(claim.documentVersion()).isEqualTo(restored.getVersion());
            });
    assertThat(
            ingestionTaskRepository
                .findByDocumentIdAndDocumentVersion(restored.getId(), restored.getVersion())
                .orElseThrow()
                .getStatus())
        .isEqualTo(IngestionTaskStatus.PROCESSING);
  }
}
