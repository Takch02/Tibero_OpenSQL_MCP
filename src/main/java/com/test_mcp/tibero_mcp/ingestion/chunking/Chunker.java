package com.test_mcp.tibero_mcp.ingestion.chunking;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 고정 크기 + 오버랩 방식의 결정적 청킹. 같은 입력은 항상 같은 청크를 낸다(재처리 검증의 전제 조건).
@Component
@RequiredArgsConstructor
public class Chunker {

  private final ChunkingProperties chunkingProperties;

  public List<String> chunk(String content) {
    int size = chunkingProperties.size();
    int step = size - chunkingProperties.overlap();

    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < content.length()) {
      int end = Math.min(start + size, content.length());
      chunks.add(content.substring(start, end));
      if (end == content.length()) {
        break;
      }
      start += step;
    }
    return chunks;
  }
}
