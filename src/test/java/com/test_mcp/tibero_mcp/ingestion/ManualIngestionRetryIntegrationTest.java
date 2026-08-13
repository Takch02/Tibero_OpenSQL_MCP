package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.test_mcp.tibero_mcp.exception.DocumentNotFoundException;
import com.test_mcp.tibero_mcp.exception.IngestionRetryConflictException;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentVersionRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.embedding.worker.retry.max-attempts=1")
@Testcontainers
class ManualIngestionRetryIntegrationTest {

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

  @Autowired EmbeddingResultWriter embeddingResultWriter;

  @Autowired IngestionTaskRepository ingestionTaskRepository;

  @Autowired DocumentRepository documentRepository;

  @Autowired DocumentVersionRepository documentVersionRepository;

  @Autowired IngestionLogRepository ingestionLogRepository;

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired MockMvc mockMvc;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.update("DELETE FROM ingestion_log");
    jdbcTemplate.update("DELETE FROM document_chunks");
    jdbcTemplate.update("DELETE FROM ingestion_tasks");
    jdbcTemplate.update("DELETE FROM document_versions");
    jdbcTemplate.update("DELETE FROM documents");
  }

  @Test
  void 최종_실패_작업은_API로_새_재시도_사이클을_시작하고_실패_원인을_감사_이력에_보존한다() throws Exception {
    Document failed = createFailedDocument("manual-retry-success", "user-1");

    mockMvc
        .perform(
            post("/api/documents/{documentId}/ingestion/retry", failed.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ownerId":"user-1","expectedVersion":1}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.status").value("PENDING"));

    IngestionTask task = taskOf(failed);
    assertThat(task.getStatus()).isEqualTo(IngestionTaskStatus.PENDING);
    assertThat(task.getAttemptCount()).isZero();
    assertThat(task.getLastError()).isNull();
    assertThat(documentRepository.findById(failed.getId()).orElseThrow().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
    assertThat(
            documentVersionRepository
                .findByDocumentIdAndVersion(failed.getId(), failed.getVersion())
                .orElseThrow()
                .getStatus())
        .isEqualTo(DocumentStatus.PENDING);
    assertThat(ingestionLogRepository.findByDocumentId(failed.getId()))
        .anySatisfy(
            log -> {
              assertThat(log.getEvent()).isEqualTo(IngestionEvent.MANUAL_RETRY);
              assertThat(log.getStatus()).isEqualTo(DocumentStatus.PENDING);
              assertThat(log.getDetails()).contains("최종 실패 재현");
            });
  }

  @Test
  void PENDING_PROCESSING_EMBEDDED_작업의_수동_재처리를_차단한다() {
    Document document =
        ingestionService.upload("manual-retry-conflict", "제목", "본문", "user-1", null);

    assertThatThrownByRetry(document, IngestionTaskStatus.PENDING);

    ingestionTaskClaimer.claimPendingTasks(1);
    assertThatThrownByRetry(document, IngestionTaskStatus.PROCESSING);

    jdbcTemplate.update(
        "UPDATE ingestion_tasks SET status = 'EMBEDDED' WHERE id = ?", taskOf(document).getId());
    assertThatThrownByRetry(document, IngestionTaskStatus.EMBEDDED);
  }

  @Test
  void 다른_소유자와_삭제된_문서는_수동_재처리할_수_없다() {
    Document failed = createFailedDocument("manual-retry-owner", "user-1");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> ingestionService.retryFailedIngestion(failed.getId(), "other-user", 1))
        .isInstanceOf(DocumentNotFoundException.class);

    ingestionService.delete(failed.getId(), "user-1", 1);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> ingestionService.retryFailedIngestion(failed.getId(), "user-1", 1))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  private Document createFailedDocument(String idempotencyKey, String ownerId) {
    Document document = ingestionService.upload(idempotencyKey, "제목", "본문", ownerId, null);
    IngestionTaskClaim claim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();
    embeddingResultWriter.handleFailure(
        claim.taskId(), claim.documentId(), claim.documentVersion(), claim.workerId(), "최종 실패 재현");
    return document;
  }

  private void assertThatThrownByRetry(Document document, IngestionTaskStatus expectedStatus) {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> ingestionService.retryFailedIngestion(document.getId(), "user-1", 1))
        .isInstanceOf(IngestionRetryConflictException.class)
        .hasMessageContaining("taskStatus=" + expectedStatus);
  }

  private IngestionTask taskOf(Document document) {
    return ingestionTaskRepository
        .findByDocumentIdAndDocumentVersion(document.getId(), document.getVersion())
        .orElseThrow();
  }
}
