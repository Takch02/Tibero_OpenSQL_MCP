package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.test_mcp.tibero_mcp.exception.DocumentNotFoundException;
import com.test_mcp.tibero_mcp.exception.DocumentVersionConflictException;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentChunk;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentStatus;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentVersion;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionEvent;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionLog;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTask;
import com.test_mcp.tibero_mcp.ingestion.entity.IngestionTaskStatus;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentVersionRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionLogRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Transactional
class IngestionServiceIntegrationTest {

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

  @Autowired DocumentRepository documentRepository;

  @Autowired DocumentChunkRepository documentChunkRepository;

  @Autowired IngestionLogRepository ingestionLogRepository;

  @Autowired IngestionTaskRepository ingestionTaskRepository;

  @Autowired IngestionTaskClaimer ingestionTaskClaimer;

  @Autowired DocumentVersionRepository documentVersionRepository;

  @Test
  void 문서를_업로드하면_청크와_로그가_임베딩_없이_한_트랜잭션으로_저장된다() {
    // when
    Document saved = ingestionService.upload("key-1", "문서 제목", "가".repeat(120), "user-1", null);

    // then: 문서
    assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(saved.getVersion()).isEqualTo(1);
    assertThat(saved.getCurrentSearchVersion()).isNull();

    // then: 청크 — 120자, 500/50 설정이면 1개 청크, embedding은 NULL
    List<DocumentChunk> chunks =
        documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(saved.getId());
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).getEmbedding()).isNull();
    assertThat(chunks.get(0).getDocumentVersion()).isEqualTo(1);

    // then: 수집 로그
    List<IngestionLog> logs = ingestionLogRepository.findByDocumentId(saved.getId());
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getEvent()).isEqualTo(IngestionEvent.CREATED);
    assertThat(logs.get(0).getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(logs.get(0).getDocumentVersion()).isEqualTo(1);
  }

  @Test
  void 존재하지_않는_문서_버전의_작업은_저장할_수_없다() {
    Document uploaded =
        ingestionService.upload("invalid-version-key", "제목", "처리할 내용", "user-1", null);

    assertThatThrownBy(
            () ->
                ingestionTaskRepository.saveAndFlush(
                    new IngestionTask(uploaded.getId(), uploaded.getVersion() + 1)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 긴_문서는_여러_청크로_나뉘어_순서대로_저장된다() {
    // 크기/오버랩에 따른 청킹 세부 로직 자체는 ChunkerTest(단위테스트)에서 검증한다.
    // 여기서는 IngestionService가 Chunker 결과를 실제로 chunk_index 순서대로 영속화하는지만 확인한다.
    Document saved = ingestionService.upload("key-a", "A", "a".repeat(1200), "user-1", null);

    List<DocumentChunk> chunks =
        documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(saved.getId());

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks)
        .extracting(DocumentChunk::getChunkIndex)
        .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
  }

  @Test
  void 같은_idempotency_key로_재시도하면_문서가_새로_생기지_않는다() {
    // given
    Document first = ingestionService.upload("retry-key", "제목", "내용", "user-1", null);

    // when: 동일 idempotency_key로 재시도
    Document retried = ingestionService.upload("retry-key", "제목", "내용", "user-1", null);

    // then
    assertThat(retried.getId()).isEqualTo(first.getId());
    assertThat(documentRepository.findAll()).hasSize(1);
  }

  @Test
  void 다른_소유자가_같은_내용을_업로드하면_별도_문서로_저장된다() {
    // given
    Document first = ingestionService.upload("req-1", "제목1", "동일한 내용", "user-1", null);

    // when: idempotency_key는 다르지만 content는 동일
    Document second = ingestionService.upload("req-2", "제목2", "동일한 내용", "user-2", null);

    // then: 내용 해시가 같더라도 다른 소유자의 문서를 합치지 않아 권한 경계를 보존한다.
    assertThat(second.getId()).isNotEqualTo(first.getId());
    assertThat(documentRepository.findAll()).hasSize(2);
  }

  @Test
  void 문서를_수정하면_과거_버전은_보존하고_새_버전을_PENDING으로_생성한다() {
    // given
    Document first = ingestionService.upload("version-key", "정책", "초기 정책", "user-1", "security");

    // when
    Document updated =
        ingestionService.update(first.getId(), "user-1", 1, "정책", "MFA가 필요한 변경 정책", "security");

    // then
    assertThat(updated.getVersion()).isEqualTo(2);
    assertThat(updated.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(updated.getCurrentSearchVersion()).isNull();

    assertThat(ingestionService.getVersions(first.getId(), "user-1"))
        .extracting(DocumentVersion::getVersion)
        .containsExactly(2, 1);
    assertThat(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(first.getId()))
        .extracting(DocumentChunk::getDocumentVersion)
        .containsExactlyInAnyOrder(1, 2);
    assertThat(ingestionLogRepository.findByDocumentId(first.getId()))
        .extracting(IngestionLog::getEvent)
        .containsExactlyInAnyOrder(IngestionEvent.CREATED, IngestionEvent.UPDATED);
  }

  @Test
  void 현재_버전과_다른_expectedVersion으로_수정하면_충돌을_반환한다() {
    Document first = ingestionService.upload("conflict-key", "정책", "초기 정책", "user-1", null);

    assertThatThrownBy(
            () -> ingestionService.update(first.getId(), "user-1", 2, "정책", "수정 정책", null))
        .isInstanceOf(DocumentVersionConflictException.class);
  }

  @Test
  void 문서를_삭제하면_검색과_일반_조회에서_제외하고_이력은_보존한다() {
    Document first = ingestionService.upload("delete-key", "정책", "삭제할 정책", "user-1", null);

    ingestionService.delete(first.getId(), "user-1", 1);

    Document deleted = documentRepository.findById(first.getId()).orElseThrow();
    assertThat(deleted.getStatus()).isEqualTo(DocumentStatus.DELETED);
    assertThat(deleted.getDeletedAt()).isNotNull();
    assertThatThrownBy(() -> ingestionService.getDocument(first.getId(), "user-1"))
        .isInstanceOf(DocumentNotFoundException.class);
    assertThat(documentVersionRepository.findByDocumentIdOrderByVersionDesc(first.getId()))
        .hasSize(1);
  }

  @Test
  void 삭제된_문서를_과거_버전으로_복원하면_새_PENDING_버전이_생긴다() {
    Document first = ingestionService.upload("restore-key", "정책", "첫 번째 정책", "user-1", null);
    Document updated = ingestionService.update(first.getId(), "user-1", 1, "정책", "두 번째 정책", null);
    ingestionService.delete(updated.getId(), "user-1", 2);

    Document restored = ingestionService.restore(updated.getId(), "user-1", 2, 1);

    assertThat(restored.getVersion()).isEqualTo(3);
    assertThat(restored.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(restored.getDeletedAt()).isNull();
    assertThat(ingestionService.getVersions(restored.getId(), "user-1"))
        .extracting(DocumentVersion::getVersion)
        .containsExactly(3, 2, 1);
    assertThat(ingestionLogRepository.findByDocumentId(restored.getId()))
        .extracting(IngestionLog::getEvent)
        .containsExactlyInAnyOrder(
            IngestionEvent.CREATED,
            IngestionEvent.UPDATED,
            IngestionEvent.DELETED,
            IngestionEvent.RESTORED);

    assertThat(
            ingestionTaskRepository
                .findByDocumentIdAndDocumentVersion(restored.getId(), restored.getVersion())
                .orElseThrow()
                .getStatus())
        .isEqualTo(IngestionTaskStatus.PENDING);
  }
}
