package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.test_mcp.tibero_mcp.exception.DocumentNotFoundException;
import com.test_mcp.tibero_mcp.exception.IncompleteEmbeddingException;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
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

  @Autowired IngestionService ingestionService;

  @Autowired IngestionTaskClaimer ingestionTaskClaimer;

  @Autowired DocumentRepository documentRepository;

  @Autowired IngestionTaskRepository ingestionTaskRepository;

  @Test
  void 존재하지_않는_documentId면_DocumentNotFoundException을_던진다() {
    Long missingId = -1L;

    assertThatThrownBy(() -> embeddingResultWriter.completeEmbedding(-1L, missingId, 1, "worker"))
        .isInstanceOf(DocumentNotFoundException.class)
        .hasMessageContaining(String.valueOf(missingId));

    assertThatThrownBy(
            () ->
                embeddingResultWriter.handleFailure(
                    -1L,
                    missingId,
                    1,
                    "worker",
                    IngestionFailureSummary.from(new RuntimeException("실패"))))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void 미임베딩_청크가_남아있으면_검색_버전을_전환하지_않는다() {
    Document uploaded = ingestionService.upload("incomplete-key", "제목", "미완료 청크", "user-1", null);
    IngestionTaskClaim claim = ingestionTaskClaimer.claimPendingTasks(1).getFirst();

    assertThatThrownBy(
            () ->
                embeddingResultWriter.completeEmbedding(
                    claim.taskId(), claim.documentId(), claim.documentVersion(), claim.workerId()))
        .isInstanceOf(IncompleteEmbeddingException.class)
        .hasMessageContaining("미임베딩 청크");

    Document reloaded = documentRepository.findById(uploaded.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(reloaded.getCurrentSearchVersion()).isNull();
    assertThat(ingestionTaskRepository.findById(claim.taskId()).orElseThrow().getStatus())
        .isEqualTo(IngestionTaskStatus.PROCESSING);
  }
}
