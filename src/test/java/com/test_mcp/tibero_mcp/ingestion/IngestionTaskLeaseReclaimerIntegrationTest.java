package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentVersionRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

@SpringBootTest
@TestPropertySource(properties = "app.embedding.worker.lease.reclaim-interval-ms=3600000")
@Testcontainers
class IngestionTaskLeaseReclaimerIntegrationTest {

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

  @Autowired IngestionTaskLeaseReclaimer ingestionTaskLeaseReclaimer;

  @Autowired IngestionTaskLeaseReclaimScheduler ingestionTaskLeaseReclaimScheduler;

  @Autowired EmbeddingResultWriter embeddingResultWriter;

  @Autowired IngestionTaskRepository ingestionTaskRepository;

  @Autowired DocumentRepository documentRepository;

  @Autowired DocumentVersionRepository documentVersionRepository;

  @Autowired IngestionLogRepository ingestionLogRepository;

  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearDatabase() {
    // Spring 테스트 컨텍스트가 다른 통합 테스트와 재사용될 수 있으므로 claim 대상 작업을 격리한다.
    jdbcTemplate.update("DELETE FROM ingestion_log");
    jdbcTemplate.update("DELETE FROM document_chunks");
    jdbcTemplate.update("DELETE FROM ingestion_tasks");
    jdbcTemplate.update("DELETE FROM document_versions");
    jdbcTemplate.update("DELETE FROM documents");
  }

  @Test
  void 만료된_PROCESSING_작업은_한번만_회수되어_다시_claim할_수_있다() throws Exception {
    Document uploaded = ingestionService.upload("lease-key", "제목", "처리할 내용", "user-1", null);
    IngestionTaskClaim firstClaim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();
    IngestionTask processingTask =
        ingestionTaskRepository.findById(firstClaim.taskId()).orElseThrow();

    assertThat(processingTask.getStatus()).isEqualTo(IngestionTaskStatus.PROCESSING);
    assertThat(processingTask.getClaimedBy()).isEqualTo(firstClaim.workerId());
    assertThat(processingTask.getLeaseExpiresAt()).isNotNull();
    jdbcTemplate.update(
        "UPDATE ingestion_tasks SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?",
        firstClaim.taskId());
    Instant beforeReclaim = Instant.now();

    ExecutorService reclaimers = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> firstReclaim =
          reclaimers.submit(ingestionTaskLeaseReclaimer::reclaimExpiredTasks);
      Future<Integer> secondReclaim =
          reclaimers.submit(ingestionTaskLeaseReclaimer::reclaimExpiredTasks);

      assertThat(firstReclaim.get(5, TimeUnit.SECONDS) + secondReclaim.get(5, TimeUnit.SECONDS))
          .isEqualTo(1);
    } finally {
      reclaimers.shutdownNow();
    }

    IngestionTask reclaimedTask =
        ingestionTaskRepository.findById(firstClaim.taskId()).orElseThrow();
    assertThat(reclaimedTask.getStatus()).isEqualTo(IngestionTaskStatus.PENDING);
    assertThat(reclaimedTask.getClaimedBy()).isNull();
    assertThat(reclaimedTask.getHeartbeatAt()).isNull();
    assertThat(reclaimedTask.getLeaseExpiresAt()).isNull();
    assertThat(reclaimedTask.getLastError()).contains("lease expired");
    assertThat(reclaimedTask.getNextAttemptAt()).isAfter(beforeReclaim);
    assertThat(ingestionLogRepository.findByDocumentId(uploaded.getId()))
        .extracting(IngestionLog::getEvent)
        .contains(IngestionEvent.LEASE_EXPIRED);

    jdbcTemplate.update(
        "UPDATE ingestion_tasks SET next_attempt_at = CURRENT_TIMESTAMP WHERE id = ?",
        firstClaim.taskId());
    IngestionTaskClaim secondClaim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();
    assertThat(secondClaim.taskId()).isEqualTo(firstClaim.taskId());
    assertThat(ingestionTaskRepository.findById(firstClaim.taskId()).orElseThrow())
        .satisfies(
            task -> {
              assertThat(task.getStatus()).isEqualTo(IngestionTaskStatus.PROCESSING);
              assertThat(task.getAttemptCount()).isEqualTo(2);
              assertThat(task.getClaimedBy()).isEqualTo(secondClaim.workerId());
            });
  }

  @Test
  void 현재_소유자의_heartbeat는_PROCESSING_lease를_연장한다() {
    Document uploaded =
        ingestionService.upload("heartbeat-key", "제목", "heartbeat를 확인할 내용", "user-1", null);
    IngestionTaskClaim claim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();
    Instant initialLeaseExpiresAt =
        ingestionTaskRepository.findById(claim.taskId()).orElseThrow().getLeaseExpiresAt();

    assertThat(embeddingResultWriter.renewLease(claim.taskId(), claim.workerId())).isTrue();

    IngestionTask renewedTask = ingestionTaskRepository.findById(claim.taskId()).orElseThrow();
    assertThat(renewedTask.getHeartbeatAt()).isNotNull();
    assertThat(renewedTask.getLeaseExpiresAt()).isAfter(initialLeaseExpiresAt);
    assertThat(renewedTask.getStatus()).isEqualTo(IngestionTaskStatus.PROCESSING);
    assertThat(renewedTask.getDocumentId()).isEqualTo(uploaded.getId());
  }

  @Test
  void 스케줄러는_별도_Bean의_회수_트랜잭션을_호출한다() {
    Document uploaded = ingestionService.upload("scheduler-key", "제목", "회수할 내용", "user-1", null);
    IngestionTaskClaim claim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();
    jdbcTemplate.update(
        "UPDATE ingestion_tasks SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?",
        claim.taskId());

    ingestionTaskLeaseReclaimScheduler.reclaimExpiredTasksOnSchedule();

    assertThat(ingestionTaskRepository.findById(claim.taskId()).orElseThrow().getStatus())
        .isEqualTo(IngestionTaskStatus.PENDING);
    assertThat(documentRepository.findById(uploaded.getId()).orElseThrow().getStatus())
        .isEqualTo(DocumentStatus.PENDING);

    // 다른 테스트의 전역 claim 대상에 남지 않도록 회수된 작업을 다시 점유한다.
    jdbcTemplate.update(
        "UPDATE ingestion_tasks SET next_attempt_at = CURRENT_TIMESTAMP WHERE id = ?",
        claim.taskId());
    assertThat(ingestionTaskClaimer.claimPendingTasks(1).getFirst().taskId())
        .isEqualTo(claim.taskId());
  }

  @Test
  void lease_만료가_재시도_상한에_도달하면_문서_버전과_작업을_FAILED로_전이한다() {
    Document uploaded =
        ingestionService.upload("lease-final-failure", "제목", "회수 실패 내용", "user-1", null);
    IngestionTaskClaim claim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();
    jdbcTemplate.update(
        """
        UPDATE ingestion_tasks
        SET attempt_count = 3,
            lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
        WHERE id = ?
        """,
        claim.taskId());

    assertThat(ingestionTaskLeaseReclaimer.reclaimExpiredTasks()).isEqualTo(1);

    assertThat(ingestionTaskRepository.findById(claim.taskId()).orElseThrow())
        .satisfies(
            task -> {
              assertThat(task.getStatus()).isEqualTo(IngestionTaskStatus.FAILED);
              assertThat(task.getLastError()).contains("lease expired");
            });
    assertThat(documentRepository.findById(uploaded.getId()).orElseThrow().getStatus())
        .isEqualTo(DocumentStatus.FAILED);
    assertThat(
            documentVersionRepository
                .findByDocumentIdAndVersion(uploaded.getId(), uploaded.getVersion())
                .orElseThrow()
                .getStatus())
        .isEqualTo(DocumentStatus.FAILED);
    assertThat(ingestionLogRepository.findByDocumentId(uploaded.getId()))
        .anySatisfy(
            log -> {
              assertThat(log.getEvent()).isEqualTo(IngestionEvent.LEASE_EXPIRED);
              assertThat(log.getStatus()).isEqualTo(DocumentStatus.FAILED);
            });
  }

  @Test
  void lease_소유자가_아니면_완료_처리로_검색_버전을_바꾸지_않는다() {
    Document uploaded = ingestionService.upload("owner-key", "제목", "소유자 확인 내용", "user-1", null);
    IngestionTaskClaim claim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();

    assertThat(
            embeddingResultWriter.completeEmbedding(
                claim.taskId(), claim.documentId(), claim.documentVersion(), "expired-worker"))
        .isFalse();

    assertThat(ingestionTaskRepository.findById(claim.taskId()).orElseThrow().getStatus())
        .isEqualTo(IngestionTaskStatus.PROCESSING);
    assertThat(documentRepository.findById(uploaded.getId()).orElseThrow().getStatus())
        .isEqualTo(DocumentStatus.PENDING);
  }
}
