package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

  @Test
  void 두_워커가_동시에_claim하면_하나만_PROCESSING으로_전이된다() throws Exception {
    Document uploaded = ingestionService.upload("claim-key", "제목", "처리할 내용", "user-1", null);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(2);

    try {
      Future<List<IngestionTaskClaim>> first = workers.submit(() -> claimAfterStart(ready, start));
      Future<List<IngestionTaskClaim>> second = workers.submit(() -> claimAfterStart(ready, start));

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      List<IngestionTaskClaim> claims = new ArrayList<>(first.get(5, TimeUnit.SECONDS));
      claims.addAll(second.get(5, TimeUnit.SECONDS));

      assertThat(claims).hasSize(1);
      assertThat(claims.getFirst().documentId()).isEqualTo(uploaded.getId());
      assertThat(
              ingestionTaskRepository
                  .findByDocumentIdAndDocumentVersion(uploaded.getId(), uploaded.getVersion())
                  .orElseThrow()
                  .getStatus())
          .isEqualTo(IngestionTaskStatus.PROCESSING);
    } finally {
      workers.shutdownNow();
    }
  }

  private List<IngestionTaskClaim> claimAfterStart(CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    return ingestionTaskClaimer.claimPendingTasks(1);
  }
}
