package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.test_mcp.tibero_mcp.embedding.EmbeddingService;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentChunk;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 청크가 많은 문서(수백 페이지 PDF 상황을 모사)를 embed-batch-size보다 잘게 나눠
// embedAll을 여러 번 호출하는지 검증한다. embed-batch-size를 2로 낮춰 5개 청크가
// 3번(2+2+1)으로 쪼개지는지 확인한다.
@SpringBootTest
@TestPropertySource(properties = "app.embedding.worker.embed-batch-size=2")
@Testcontainers
class EmbeddingWorkerBatchingIntegrationTest {

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

  @Test
  void 청크가_많은_문서는_embed_batch_size_단위로_나눠_추론한다() {
    // given: 500/50 청킹 설정에서 5개 청크가 나오는 길이(500 + 4*450 = 2300)
    Document uploaded =
        ingestionService.upload("batching-key", "제목", "d".repeat(2300), "user-1", null);
    List<DocumentChunk> chunks =
        documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(uploaded.getId());
    assertThat(chunks).hasSize(5);

    given(embeddingService.embedAll(anyList()))
        .willAnswer(
            invocation -> {
              List<?> input = invocation.getArgument(0);
              return input.stream().map(t -> new float[] {0f}).toList();
            });

    // when
    embeddingWorker.pollAndProcess();

    // then: 5개 청크가 batch-size=2로 3번(2+2+1) 호출됨
    verify(embeddingService, times(3)).embedAll(anyList());

    Document reloaded = documentRepository.findById(uploaded.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.EMBEDDED);
  }
}
