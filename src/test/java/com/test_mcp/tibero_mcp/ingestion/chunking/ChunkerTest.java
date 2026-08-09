package com.test_mcp.tibero_mcp.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

// DB/Spring 컨텍스트가 필요 없는 순수 로직이라 단위테스트로 검증한다.
class ChunkerTest {

  private final Chunker chunker = new Chunker(new ChunkingProperties(500, 50));

  @Test
  void 짧은_문서는_청크_1개로_그대로_반환된다() {
    String content = "가".repeat(120);

    List<String> chunks = chunker.chunk(content);

    assertThat(chunks).containsExactly(content);
  }

  @Test
  void 긴_문서는_크기와_오버랩_설정대로_결정적으로_분할된다() {
    // 500자/50자 오버랩 → step 450. 시작 오프셋 0, 450, 900 → 청크 3개(500/500/300자)
    String content = "a".repeat(1200);

    List<String> chunks = chunker.chunk(content);

    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0)).hasSize(500);
    assertThat(chunks.get(1)).hasSize(500);
    assertThat(chunks.get(2)).hasSize(300);
  }

  @Test
  void 같은_내용은_항상_같은_청크로_분할된다() {
    String content = "b".repeat(1200);

    assertThat(chunker.chunk(content)).isEqualTo(chunker.chunk(content));
  }
}
