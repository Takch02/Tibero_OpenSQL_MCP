package com.test_mcp.tibero_mcp.document;

import java.util.List;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentService {

  private final DocumentRepository documentRepository;

  @Transactional
  public Document upload(String title, String content, float[] embedding) {
    return documentRepository.save(new Document(title, content, toVectorString(embedding)));
  }

  @Transactional(readOnly = true)
  public List<Document> searchSimilar(float[] queryEmbedding, int limit) {
    return documentRepository.findNearestNeighbors(toVectorString(queryEmbedding), limit);
  }

  // float[] → "[0.1,0.2,0.3]" 형식으로 변환 (pgvector 리터럴)
  private String toVectorString(float[] embedding) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    for (float v : embedding) {
      joiner.add(String.valueOf(v));
    }
    return joiner.toString();
  }
}
