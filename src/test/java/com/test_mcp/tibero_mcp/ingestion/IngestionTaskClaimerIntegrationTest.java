package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@TestPropertySource(properties = "app.embedding.worker.retry.max-attempts=1")
@Testcontainers
class IngestionTaskClaimerIntegrationTest {

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

  @Autowired DocumentRepository documentRepository;

  @Autowired EmbeddingResultWriter embeddingResultWriter;

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void 잠긴_PENDING_작업은_다른_워커가_대기하지_않고_건너뛴다() throws Exception {
    Document uploaded = ingestionService.upload("claim-key", "제목", "처리할 내용", "user-1", null);
    CountDownLatch lockAcquired = new CountDownLatch(1);
    CountDownLatch releaseLock = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);

    try {
      Future<?> lockHolder =
          workers.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .executeWithoutResult(
                          status -> {
                            List<IngestionTask> lockedTasks =
                                ingestionTaskRepository.findPendingForUpdateSkipLocked(1);
                            assertThat(lockedTasks).singleElement();
                            lockAcquired.countDown();
                            await(releaseLock);
                          }));

      assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

      Future<List<IngestionTaskClaim>> claimAttempt =
          workers.submit(() -> ingestionTaskClaimer.claimPendingTasks(1));

      assertThat(claimAttempt.get(1, TimeUnit.SECONDS)).isEmpty();
      releaseLock.countDown();
      lockHolder.get(5, TimeUnit.SECONDS);

      assertThat(ingestionTaskClaimer.claimPendingTasks(1))
          .singleElement()
          .satisfies(
              claim -> {
                assertThat(claim.documentId()).isEqualTo(uploaded.getId());
                assertThat(claim.documentVersion()).isEqualTo(uploaded.getVersion());
              });
      assertThat(
              ingestionTaskRepository
                  .findByDocumentIdAndDocumentVersion(uploaded.getId(), uploaded.getVersion())
                  .orElseThrow()
                  .getStatus())
          .isEqualTo(IngestionTaskStatus.PROCESSING);
    } finally {
      releaseLock.countDown();
      workers.shutdownNow();
    }
  }

  @Test
  void FAILED_문서의_PENDING_재시도_작업을_claim한다() {
    Document uploaded = ingestionService.upload("failed-retry-key", "제목", "처리할 내용", "user-1", null);
    IngestionTask task =
        ingestionTaskRepository
            .findByDocumentIdAndDocumentVersion(uploaded.getId(), uploaded.getVersion())
            .orElseThrow();
    IngestionTaskClaim initialClaim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();
    embeddingResultWriter.handleFailure(
        task.getId(), uploaded.getId(), uploaded.getVersion(), initialClaim.workerId(), "최종 실패 재현");

    // FAILED 문서에 PENDING 작업을 구성해 문서 상태가 claim 조건을 제한하지 않는지 검증한다.
    assertThat(ingestionTaskRepository.findById(task.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionTaskStatus.FAILED);
    jdbcTemplate.update(
        "UPDATE ingestion_tasks SET status = 'PENDING', next_attempt_at = CURRENT_TIMESTAMP WHERE id = ?",
        task.getId());

    assertThat(documentRepository.findById(uploaded.getId()).orElseThrow().getStatus())
        .isEqualTo(DocumentStatus.FAILED);
    assertThat(ingestionTaskClaimer.claimPendingTasks(1))
        .singleElement()
        .satisfies(
            claim -> {
              assertThat(claim.documentId()).isEqualTo(uploaded.getId());
              assertThat(claim.documentVersion()).isEqualTo(uploaded.getVersion());
            });
    assertThat(ingestionTaskRepository.findById(task.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionTaskStatus.PROCESSING);
  }

  @Test
  void 다음_실행_시각이_도래하지_않은_PENDING_작업은_claim하지_않는다() {
    Document uploaded = ingestionService.upload("future-retry-key", "제목", "처리할 내용", "user-1", null);
    jdbcTemplate.update(
        "UPDATE ingestion_tasks SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE document_id = ?",
        uploaded.getId());

    assertThat(ingestionTaskClaimer.claimPendingTasks(1)).isEmpty();
    assertThat(
            ingestionTaskRepository
                .findByDocumentIdAndDocumentVersion(uploaded.getId(), uploaded.getVersion())
                .orElseThrow()
                .getStatus())
        .isEqualTo(IngestionTaskStatus.PENDING);
  }

  private void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("작업 잠금 대기 중 인터럽트되었습니다.", e);
    }
  }
}
