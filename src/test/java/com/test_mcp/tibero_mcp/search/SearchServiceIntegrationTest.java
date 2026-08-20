package com.test_mcp.tibero_mcp.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.test_mcp.tibero_mcp.embedding.EmbeddingService;
import com.test_mcp.tibero_mcp.ingestion.EmbeddingWorker;
import com.test_mcp.tibero_mcp.ingestion.IngestionService;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import com.test_mcp.tibero_mcp.ingestion.repository.ChunkSearchProjection;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
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

// 정형 데이터(owner_id 권한, category 메타데이터)와 벡터 유사도를 한 쿼리로 결합한 검색을 검증한다.
// 임베딩 모델은 목킹하고 고정 벡터를 써서, 필터링(권한/카테고리)이 올바르게 동작하는지에 집중한다.
@SpringBootTest
@Testcontainers
class SearchServiceIntegrationTest {

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

  @Test
  void owner_id와_category로_정형_필터링하고_벡터_유사도로_정렬한다() {
    // given: 같은 카테고리·다른 소유자, 같은 소유자·다른 카테고리 문서를 섞어둔다.
    Document owned =
        ingestionService.upload("key-1", "고양이", "Cats are cute pets.", "user-1", "animal");
    Document otherOwner =
        ingestionService.upload("key-2", "강아지", "Dogs are loyal pets.", "user-2", "animal");
    Document otherCategory =
        ingestionService.upload("key-3", "엔진", "Car engines convert fuel.", "user-1", "vehicle");

    given(embeddingService.embed(anyString())).willReturn(fixedVector());
    given(embeddingService.embedAll(anyList()))
        .willAnswer(
            invocation -> {
              List<?> input = invocation.getArgument(0);
              return input.stream().map(t -> fixedVector()).toList();
            });
    given(embeddingService.toVectorLiteral(any())).willAnswer(inv -> toLiteral(inv.getArgument(0)));

    embeddingWorker.pollAndProcess();

    // when: user-1의 animal 카테고리만 검색
    List<ChunkSearchProjection> filtered =
        searchService.searchSimilar("pets", "user-1", "animal", 10);

    // then: 다른 소유자(otherOwner), 다른 카테고리(otherCategory) 문서는 걸러진다.
    assertThat(filtered)
        .extracting(ChunkSearchProjection::getDocumentId)
        .containsExactly(owned.getId());
    assertThat(filtered).extracting(ChunkSearchProjection::getDocumentVersion).containsExactly(1);
    assertThat(filtered).extracting(ChunkSearchProjection::getDocumentTitle).containsExactly("고양이");
    assertThat(filtered).extracting(ChunkSearchProjection::getCategory).containsExactly("animal");
    assertThat(filtered.get(0).getScore())
        .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));

    // when: category 없이 user-1 전체 검색
    List<ChunkSearchProjection> allForOwner =
        searchService.searchSimilar("pets", "user-1", null, 10);

    // then: user-1이 소유한 두 문서만 반환(otherOwner는 제외)
    assertThat(allForOwner)
        .extracting(ChunkSearchProjection::getDocumentId)
        .containsExactlyInAnyOrder(owned.getId(), otherCategory.getId());

    // when: 다른 소유자로 검색
    List<ChunkSearchProjection> forOtherOwner =
        searchService.searchSimilar("pets", "user-2", null, 10);

    // then: user-2 소유 문서만 반환
    assertThat(forOtherOwner)
        .extracting(ChunkSearchProjection::getDocumentId)
        .containsExactly(otherOwner.getId());
  }

  @Test
  void 새_버전이_PENDING이면_마지막_정상_검색_버전과_현재_메타데이터를_반환한다() {
    Document document =
        ingestionService.upload(
            "key-version", "초기 보안 정책", "관리자 계정은 MFA를 사용합니다.", "version-user", "security");
    given(embeddingService.embed(anyString())).willReturn(fixedVector());
    given(embeddingService.embedAll(anyList()))
        .willAnswer(
            invocation -> {
              List<?> input = invocation.getArgument(0);
              return input.stream().map(t -> fixedVector()).toList();
            });
    given(embeddingService.toVectorLiteral(any())).willAnswer(inv -> toLiteral(inv.getArgument(0)));
    embeddingWorker.pollAndProcess();

    // v2는 아직 임베딩하지 않아도, 검색은 마지막 정상 버전(v1)을 계속 사용해야 한다.
    ingestionService.update(
        document.getId(), "version-user", 1, "개정 보안 정책", "관리자 계정은 강화된 MFA를 사용합니다.", "security");

    List<ChunkSearchProjection> result =
        searchService.searchSimilar("관리자 계정", "version-user", "security", 10);

    assertThat(result)
        .extracting(
            ChunkSearchProjection::getDocumentId,
            ChunkSearchProjection::getDocumentTitle,
            ChunkSearchProjection::getDocumentVersion,
            ChunkSearchProjection::getCategory)
        .contains(org.assertj.core.groups.Tuple.tuple(document.getId(), "개정 보안 정책", 1, "security"));
  }

  private static float[] fixedVector() {
    float[] vector = new float[384];
    Arrays.fill(vector, 0.1f);
    return vector;
  }

  private static String toLiteral(float[] embedding) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    for (float v : embedding) {
      joiner.add(String.valueOf(v));
    }
    return joiner.toString();
  }
}
