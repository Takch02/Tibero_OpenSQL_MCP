package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.test_mcp.tibero_mcp.embedding.EmbeddingService;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentVersionRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import com.test_mcp.tibero_mcp.search.SearchService;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// OpenSQL smoke 프로필의 실패 주입이 실제 Worker → ResultWriter → 수동 재처리 경로를 통과하는지 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("opensql-smoke")
@TestPropertySource(
    properties = {
      "spring.task.scheduling.enabled=false",
      "app.embedding.chaos.fail-first-batches=3",
      "app.embedding.worker.retry.max-attempts=3",
      "app.embedding.worker.retry.initial-backoff-ms=0",
      "app.embedding.worker.retry.max-backoff-ms=0"
    })
@Testcontainers
class OpenSqlSmokeFailureRecoveryIntegrationTest {

  private static final String OWNER_ID = "opensql-smoke-owner";
  private static final String CATEGORY = "opensql-smoke";
  private static final String FAILURE_MARKER = "[[OPENSQL_SMOKE_FAIL]]";
  private static final String V1_CONTENT = "이 문서는 실패 중에도 계속 검색되는 안정 버전입니다.";
  private static final String V2_CONTENT = FAILURE_MARKER + " 수동 재처리 뒤 검색으로 전환되는 새 버전입니다.";

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

  @MockitoBean EmbeddingService embeddingService;

  @Autowired IngestionService ingestionService;
  @Autowired EmbeddingWorker embeddingWorker;
  @Autowired SearchService searchService;
  @Autowired DocumentRepository documentRepository;
  @Autowired DocumentVersionRepository documentVersionRepository;
  @Autowired DocumentChunkRepository documentChunkRepository;
  @Autowired IngestionTaskRepository ingestionTaskRepository;
  @Autowired IngestionLogRepository ingestionLogRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM ingestion_log");
    jdbcTemplate.update("DELETE FROM document_chunks");
    jdbcTemplate.update("DELETE FROM ingestion_tasks");
    jdbcTemplate.update("DELETE FROM document_versions");
    jdbcTemplate.update("DELETE FROM documents");

    given(embeddingService.embedDocuments(anyList()))
        .willAnswer(
            invocation -> {
              List<String> contents = invocation.getArgument(0);
              return contents.stream().map(ignored -> vector()).toList();
            });
    given(embeddingService.embedQuery(any(String.class))).willReturn(vector());
    given(embeddingService.toVectorLiteral(any(float[].class))).willReturn(vectorLiteral());
  }

  @Test
  void v2_실패_동안_v1을_검색하고_수동_재처리_후_v2를_검색한다() throws Exception {
    Document uploaded =
        ingestionService.upload(
            "smoke-failure-v1", "OpenSQL 실패 복구", V1_CONTENT, OWNER_ID, CATEGORY);
    embeddingWorker.pollAndProcess();

    assertThat(reload(uploaded).getCurrentSearchVersion()).isEqualTo(1);

    Document updated =
        ingestionService.update(
            uploaded.getId(), OWNER_ID, 1, "OpenSQL 실패 복구 v2", V2_CONTENT, CATEGORY);
    for (int attempt = 0; attempt < 3; attempt++) {
      // 이 테스트는 backoff를 0ms로 단축했으므로, Java Instant와 DB CURRENT_TIMESTAMP의 미세한
      // 경계에 의존하지 않게 다음 시도 시각만 DB 기준 현재로 맞춘다.
      makeTaskDue(updated.getId(), 2);
      embeddingWorker.pollAndProcess();
    }

    IngestionTask failedTask = taskOf(updated.getId(), 2);
    assertThat(failedTask.getStatus()).isEqualTo(IngestionTaskStatus.FAILED);
    assertThat(reload(updated).getStatus()).isEqualTo(DocumentStatus.FAILED);
    assertThat(reload(updated).getCurrentSearchVersion()).isEqualTo(1);
    assertThat(
            documentVersionRepository
                .findByDocumentIdAndVersion(updated.getId(), 2)
                .orElseThrow()
                .getStatus())
        .isEqualTo(DocumentStatus.FAILED);
    assertThat(
            documentChunkRepository
                .findByDocumentIdAndDocumentVersionAndEmbeddingIsNullOrderByChunkIndexAsc(
                    updated.getId(), 2))
        .isNotEmpty();
    assertThat(searchContents()).contains(V1_CONTENT).doesNotContain(V2_CONTENT);
    verify(embeddingService, times(1)).embedDocuments(anyList());

    mockMvc
        .perform(
            post("/api/documents/{documentId}/ingestion/retry", updated.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"%s\",\"expectedVersion\":2}".formatted(OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.status").value("PENDING"));

    // 수동 재처리도 즉시 실행을 위해 Java 시각으로 예약한다. 테스트의 poll은 DB 시각으로
    // due를 판정하므로, 경계 시각에 의존하지 않도록 현재 시각을 DB 기준으로 맞춘다.
    makeTaskDue(updated.getId(), 2);
    embeddingWorker.pollAndProcess();

    assertThat(taskOf(updated.getId(), 2).getStatus()).isEqualTo(IngestionTaskStatus.EMBEDDED);
    assertThat(reload(updated).getStatus()).isEqualTo(DocumentStatus.EMBEDDED);
    assertThat(reload(updated).getCurrentSearchVersion()).isEqualTo(2);
    assertThat(
            documentChunkRepository.existsByDocumentIdAndDocumentVersionAndEmbeddingIsNull(
                updated.getId(), 2))
        .isFalse();
    assertThat(searchContents()).contains(V2_CONTENT).doesNotContain(V1_CONTENT);
    assertThat(ingestionLogRepository.findByDocumentId(updated.getId()))
        .extracting(IngestionLog::getEvent)
        .contains(IngestionEvent.FAILED, IngestionEvent.MANUAL_RETRY, IngestionEvent.EMBEDDED);
    verify(embeddingService, times(2)).embedDocuments(anyList());
  }

  private Document reload(Document document) {
    return documentRepository.findById(document.getId()).orElseThrow();
  }

  private IngestionTask taskOf(Long documentId, int version) {
    return ingestionTaskRepository
        .findByDocumentIdAndDocumentVersion(documentId, version)
        .orElseThrow();
  }

  private void makeTaskDue(Long documentId, int version) {
    jdbcTemplate.update(
        """
        UPDATE ingestion_tasks
        SET next_attempt_at = CURRENT_TIMESTAMP
        WHERE document_id = ?
          AND document_version = ?
        """,
        documentId,
        version);
  }

  private List<String> searchContents() {
    return searchService.searchSimilar("OpenSQL 실패 복구", OWNER_ID, CATEGORY, 10).stream()
        .map(result -> result.getContent())
        .toList();
  }

  private static float[] vector() {
    return new float[384];
  }

  private static String vectorLiteral() {
    return "["
        + IntStream.range(0, 384)
            .mapToObj(ignored -> "0.0")
            .collect(java.util.stream.Collectors.joining(","))
        + "]";
  }
}
