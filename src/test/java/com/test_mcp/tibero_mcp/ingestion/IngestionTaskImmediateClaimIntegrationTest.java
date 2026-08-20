package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 새 Outbox 작업은 생성 직후 DB의 CURRENT_TIMESTAMP 기준으로 claim 가능해야 한다.
@SpringBootTest
@TestPropertySource(properties = "spring.task.scheduling.enabled=false")
@Testcontainers
class IngestionTaskImmediateClaimIntegrationTest {

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
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.update("DELETE FROM ingestion_log");
    jdbcTemplate.update("DELETE FROM document_chunks");
    jdbcTemplate.update("DELETE FROM ingestion_tasks");
    jdbcTemplate.update("DELETE FROM document_versions");
    jdbcTemplate.update("DELETE FROM documents");
  }

  @Test
  void 새_작업은_생성_직후_due이고_claim할_수_있다() {
    for (int index = 0; index < 20; index++) {
      Document document =
          ingestionService.upload(
              "immediate-claim-%d".formatted(index), "제목", "처리할 내용", "user-1", null);
      Long taskId =
          ingestionTaskRepository
              .findByDocumentIdAndDocumentVersion(document.getId(), document.getVersion())
              .orElseThrow()
              .getId();

      Boolean isDue =
          jdbcTemplate.queryForObject(
              "SELECT next_attempt_at <= CURRENT_TIMESTAMP FROM ingestion_tasks WHERE id = ?",
              Boolean.class,
              taskId);

      assertThat(ingestionTaskRepository.findById(taskId).orElseThrow().getNextAttemptAt())
          .isNotNull();
      assertThat(isDue).as("taskId=%s는 생성 직후 DB 기준 due여야 합니다", taskId).isTrue();
      assertThat(ingestionTaskClaimer.claimPendingTasks(1))
          .singleElement()
          .extracting(IngestionTaskClaim::taskId)
          .isEqualTo(taskId);
    }
  }
}
