package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentChunk;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentVersionRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 워커는 커밋된 PENDING 문서를 다른 트랜잭션에서 집어가므로, 여기서는 @Transactional을 붙이지 않고
// 실제 커밋 상태로 검증한다(컨테이너는 클래스 단위로 격리됨). 임베딩 추론은 실제 ONNX 모델로 수행한다.
@SpringBootTest
@Testcontainers
class EmbeddingWorkerIntegrationTest {

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

  @Autowired EmbeddingWorker embeddingWorker;

  @Autowired DocumentRepository documentRepository;

  @Autowired DocumentChunkRepository documentChunkRepository;

  @Autowired IngestionLogRepository ingestionLogRepository;

  @Autowired DocumentVersionRepository documentVersionRepository;

  @Test
  void 워커가_PENDING_문서를_처리하면_청크가_임베딩되고_EMBEDDED로_전이된다() {
    // given: 600자 → 500/50 설정이면 청크 2개, 업로드 직후엔 embedding=NULL / status=PENDING
    Document uploaded =
        ingestionService.upload("worker-key", "제목", "b".repeat(600), "user-1", null);
    assertThat(uploaded.getStatus()).isEqualTo(DocumentStatus.PENDING);

    // when: 워커 폴링 실행(자동 스케줄은 테스트에서 비활성, 직접 호출로 결정적 검증)
    embeddingWorker.pollAndProcess();

    // then: 문서 상태 전이
    Document reloaded = documentRepository.findById(uploaded.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.EMBEDDED);

    // then: 모든 청크의 embedding이 채워짐
    List<DocumentChunk> chunks =
        documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(uploaded.getId());
    assertThat(chunks).hasSize(2);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getEmbedding()).isNotNull());

    // then: 수집 로그에 CREATED(업로드) + EMBEDDED(워커) 이력이 남음
    List<IngestionLog> logs = ingestionLogRepository.findByDocumentId(uploaded.getId());
    assertThat(logs)
        .extracting(IngestionLog::getEvent)
        .containsExactlyInAnyOrder(IngestionEvent.CREATED, IngestionEvent.EMBEDDED);
  }

  @Test
  void 이미_모두_임베딩된_문서를_다시_돌려도_중복_처리되지_않는다() {
    // given: 한 번 처리해서 EMBEDDED로 만든 문서
    Document uploaded =
        ingestionService.upload("idem-worker", "제목", "c".repeat(300), "user-1", null);
    embeddingWorker.pollAndProcess();
    int logsAfterFirst = ingestionLogRepository.findByDocumentId(uploaded.getId()).size();

    // when: 다시 폴링해도 PENDING이 아니므로 집어가지 않는다
    embeddingWorker.pollAndProcess();

    // then: 로그가 더 늘지 않음
    assertThat(ingestionLogRepository.findByDocumentId(uploaded.getId())).hasSize(logsAfterFirst);
  }

  @Test
  void 새_버전_임베딩이_완료될_때까지_이전_정상_버전이_검색_대상으로_유지된다() {
    Document uploaded = ingestionService.upload("version-worker", "정책", "첫 번째 정책", "user-1", null);
    embeddingWorker.pollAndProcess();

    Document updated =
        ingestionService.update(uploaded.getId(), "user-1", 1, "정책", "두 번째 정책", null);

    assertThat(updated.getVersion()).isEqualTo(2);
    assertThat(updated.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(updated.getCurrentSearchVersion()).isEqualTo(1);
    assertThat(
            documentVersionRepository
                .findByDocumentIdAndVersion(updated.getId(), 1)
                .orElseThrow()
                .getStatus())
        .isEqualTo(DocumentStatus.EMBEDDED);
    assertThat(
            documentVersionRepository
                .findByDocumentIdAndVersion(updated.getId(), 2)
                .orElseThrow()
                .getStatus())
        .isEqualTo(DocumentStatus.PENDING);

    embeddingWorker.pollAndProcess();

    Document embedded = documentRepository.findById(updated.getId()).orElseThrow();
    assertThat(embedded.getCurrentSearchVersion()).isEqualTo(2);
    assertThat(embedded.getStatus()).isEqualTo(DocumentStatus.EMBEDDED);
  }
}
