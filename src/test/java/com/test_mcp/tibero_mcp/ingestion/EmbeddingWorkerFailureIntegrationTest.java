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
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 추론 실패 경로 검증. EmbeddingService를 목으로 교체해 추론에서 예외를 던지게 한다.
// 업로드 경로(IngestionService.upload)는 추론을 하지 않으므로 목 교체의 영향을 받지 않는다.
@SpringBootTest
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

  @Test
  void 임베딩_추론이_실패하면_FAILED로_전이되고_청크는_NULL로_남는다() {
    // given: 업로드는 정상(추론 없음)
    Document uploaded = ingestionService.upload("fail-key", "제목", "실패할 내용", "user-1", null);
    given(embeddingService.embedAll(anyList())).willThrow(new RuntimeException("모델 추론 오류"));

    // when
    embeddingWorker.pollAndProcess();

    // then: 상태는 FAILED, 청크 embedding은 채워지지 않고 NULL 유지(재처리 여지)
    Document reloaded = documentRepository.findById(uploaded.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.FAILED);

    List<DocumentChunk> chunks =
        documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(uploaded.getId());
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getEmbedding()).isNull());

    // then: CREATED + FAILED 이력
    List<IngestionLog> logs = ingestionLogRepository.findByDocumentId(uploaded.getId());
    assertThat(logs)
        .extracting(IngestionLog::getEvent)
        .containsExactlyInAnyOrder(IngestionEvent.CREATED, IngestionEvent.FAILED);
  }
}
