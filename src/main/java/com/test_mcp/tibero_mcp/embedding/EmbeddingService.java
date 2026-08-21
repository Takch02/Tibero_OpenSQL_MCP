package com.test_mcp.tibero_mcp.embedding;

import com.test_mcp.tibero_mcp.exception.EmbeddingDimensionMismatchException;
import java.util.List;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

  private final EmbeddingModel embeddingModel;
  private final EmbeddingPrefixProperties embeddingPrefixProperties;

  @Value("${app.embedding.dimension}")
  private int dimension;

  public float[] embed(String text) {
    float[] vector = embeddingModel.embed(text);
    verifyDimension(vector);
    return vector;
  }

  // 여러 문서 청크를 한 번에 배치 추론한다. 접두사는 배치의 모든 청크에 동일하게 적용한다.
  public List<float[]> embedDocuments(List<String> contents) {
    return embedAll(
        contents.stream().map(content -> embeddingPrefixProperties.document() + content).toList());
  }

  // 검색 질의는 모델이 요구하는 질의 역할 접두사를 붙여 임베딩한다.
  public float[] embedQuery(String query) {
    return embed(embeddingPrefixProperties.query() + query);
  }

  // 여러 텍스트를 한 번에 배치 추론한다. 하나씩 N번 추론하는 것보다 훨씬 효율적이라
  // 임베딩 워커가 문서의 청크들을 묶어서 임베딩할 때 사용한다.
  public List<float[]> embedAll(List<String> texts) {
    List<float[]> vectors = embeddingModel.embed(texts);
    vectors.forEach(this::verifyDimension);
    return vectors;
  }

  private void verifyDimension(float[] vector) {
    if (vector.length != dimension) {
      throw new EmbeddingDimensionMismatchException(vector.length, dimension);
    }
  }

  // float[] → "[0.1,0.2,0.3]" 형식으로 변환 (pgvector 리터럴)
  public String toVectorLiteral(float[] embedding) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    for (float v : embedding) {
      joiner.add(String.valueOf(v));
    }
    return joiner.toString();
  }
}
