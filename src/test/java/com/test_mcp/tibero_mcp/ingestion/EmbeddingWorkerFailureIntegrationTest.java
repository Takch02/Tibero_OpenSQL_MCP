package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.test_mcp.tibero_mcp.embedding.EmbeddingService;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentChunk;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 추론 실패 경로 검증. EmbeddingService를 목으로 교체해 추론에서 예외를 던지게 한다.
// 업로드 경로(IngestionService.upload)는 추론을 하지 않으므로 목 교체의 영향을 받지 않는다.
@SpringBootTest
@TestPropertySource(properties = {"app.embedding.worker.retry.max-attempts=2"})
@Testcontainers
class EmbeddingWorkerFailureIntegrationTest {

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

  @Autowired DocumentRepository documentRepository;

  @Autowired DocumentChunkRepository documentChunkRepository;

  @Autowired IngestionLogRepository ingestionLogRepository;

  @Autowired IngestionTaskRepository ingestionTaskRepository;

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void 임베딩_추론이_실패하면_backoff_후_PENDING으로_재예약하고_최대_횟수에서_FAILED로_전이한다() {
    // given: 업로드는 정상(추론 없음)
    Document uploaded = ingestionService.upload("fail-key", "제목", "실패할 내용", "user-1", null);
    given(embeddingService.embedAll(anyList())).willThrow(new RuntimeException("모델 추론 오류"));

    // when: 첫 번째 실패는 재시도 대상으로 남긴다.
    Instant beforeFirstFailure = Instant.now();
    embeddingWorker.pollAndProcess();

    // then: 문서와 버전은 아직 검색 버전을 바꾸지 않고, task만 재시도 대기 상태가 된다.
    Document reloaded = documentRepository.findById(uploaded.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.PENDING);

    List<DocumentChunk> chunks =
        documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(uploaded.getId());
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getEmbedding()).isNull());

    assertThat(
            ingestionTaskRepository
                .findByDocumentIdAndDocumentVersion(uploaded.getId(), uploaded.getVersion())
                .orElseThrow())
        .satisfies(
            task -> {
              assertThat(task.getStatus()).isEqualTo(IngestionTaskStatus.PENDING);
              assertThat(task.getAttemptCount()).isEqualTo(1);
              assertThat(task.getLastError()).contains("EMBEDDING_INFERENCE_FAILED");
              assertThat(task.getLastError()).doesNotContain("모델 추론 오류");
              assertThat(task.getNextAttemptAt()).isAfter(beforeFirstFailure);
            });

    // when: 시간 경과를 기다리지 않고 due 상태를 재현한다.
    jdbcTemplate.update(
        "UPDATE ingestion_tasks SET next_attempt_at = CURRENT_TIMESTAMP WHERE document_id = ?",
        uploaded.getId());
    embeddingWorker.pollAndProcess();

    // then: CREATED + 최종 FAILED 이력
    List<IngestionLog> logs = ingestionLogRepository.findByDocumentId(uploaded.getId());
    assertThat(logs)
        .extracting(IngestionLog::getEvent)
        .containsExactlyInAnyOrder(IngestionEvent.CREATED, IngestionEvent.FAILED);
    assertThat(
            ingestionTaskRepository
                .findByDocumentIdAndDocumentVersion(uploaded.getId(), uploaded.getVersion())
                .orElseThrow())
        .satisfies(
            task -> {
              assertThat(task.getStatus()).isEqualTo(IngestionTaskStatus.FAILED);
              assertThat(task.getLastError()).contains("EMBEDDING_INFERENCE_FAILED");
              assertThat(task.getLastError()).doesNotContain("모델 추론 오류");
            });
    assertThat(documentRepository.findById(uploaded.getId()).orElseThrow().getStatus())
        .isEqualTo(DocumentStatus.FAILED);
  }
}
