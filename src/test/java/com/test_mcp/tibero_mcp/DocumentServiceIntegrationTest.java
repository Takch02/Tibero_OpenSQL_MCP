package com.test_mcp.tibero_mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.test_mcp.tibero_mcp.document.Document;
import com.test_mcp.tibero_mcp.document.DocumentRepository;
import com.test_mcp.tibero_mcp.document.DocumentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class DocumentServiceIntegrationTest {

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

  @Autowired DocumentService documentService;

  @Autowired DocumentRepository documentRepository;

  @Test
  void 벡터_검색_동작() {
    // given: 서로 다른 방향의 단위 벡터로 문서 3개 저장
    documentService.upload("문서A", "첫 번째 문서", new float[] {1.0f, 0.0f, 0.0f});
    documentService.upload("문서B", "두 번째 문서", new float[] {0.0f, 1.0f, 0.0f});
    documentService.upload("문서C", "세 번째 문서", new float[] {0.0f, 0.0f, 1.0f});

    // when: [1, 0, 0]에 가장 가까운 문서 1개 검색
    List<Document> results = documentService.searchSimilar(new float[] {1.0f, 0.0f, 0.0f}, 1);

    // then: 문서A가 반환되어야 함
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getTitle()).isEqualTo("문서A");
  }

  @Test
  void 유사도_순서_정렬() {
    // given: 쿼리 벡터 [1,0,0]에 가까운 순서로 A > B > C
    documentService.upload("문서A", "내용A", new float[] {1.0f, 0.0f, 0.0f});
    documentService.upload("문서B", "내용B", new float[] {0.9f, 0.1f, 0.0f});
    documentService.upload("문서C", "내용C", new float[] {0.0f, 0.0f, 1.0f});

    // when: top-2 검색
    List<Document> results = documentService.searchSimilar(new float[] {1.0f, 0.0f, 0.0f}, 2);

    // then: 거리가 가까운 A, B 순으로 반환
    assertThat(results).hasSize(2);
    assertThat(results.get(0).getTitle()).isEqualTo("문서A");
    assertThat(results.get(1).getTitle()).isEqualTo("문서B");
  }

  @Test
  void 문서_저장_및_조회() {
    // given
    documentService.upload("저장 테스트", "내용", new float[] {0.5f, 0.5f, 0.0f});

    // when
    List<Document> all = documentRepository.findAll();

    // then
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getTitle()).isEqualTo("저장 테스트");
    assertThat(all.get(0).getContent()).isEqualTo("내용");
  }
}
