package com.test_mcp.tibero_mcp.ingestion.repository;

import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// document_chunks는 GenerationType.IDENTITY라 Hibernate가 JDBC 배치를 쓸 수 없다(insert 직후
// 생성된 id를 즉시 받아야 해서 건별 flush가 강제됨). 청크 id는 저장 이후 참조되지 않으므로
// JdbcTemplate으로 우회해 한 번의 배치 INSERT로 처리한다.
@Component
@RequiredArgsConstructor
public class DocumentChunkBatchWriter {

  private static final String INSERT_SQL =
      "INSERT INTO document_chunks (document_id, document_version, chunk_index, content) "
          + "VALUES (?, ?, ?, ?)";

  private static final String UPDATE_EMBEDDING_SQL =
      "UPDATE document_chunks SET embedding = CAST(? AS vector) WHERE id = ?";

  private final JdbcTemplate jdbcTemplate;

  public void insertAll(Long documentId, Integer documentVersion, List<String> chunks) {
    List<Object[]> batchArgs =
        IntStream.range(0, chunks.size())
            .mapToObj(i -> new Object[] {documentId, documentVersion, i, chunks.get(i)})
            .toList();
    jdbcTemplate.batchUpdate(INSERT_SQL, batchArgs);
  }

  // 임베딩 워커가 추론으로 얻은 벡터를 청크별로 한 번의 배치 UPDATE로 반영한다.
  // chunkIds[i] ↔ vectorLiterals[i]가 같은 청크를 가리키도록 호출부에서 순서를 맞춰야 한다.
  public void updateEmbeddings(List<Long> chunkIds, List<String> vectorLiterals) {
    List<Object[]> batchArgs =
        IntStream.range(0, chunkIds.size())
            .mapToObj(i -> new Object[] {vectorLiterals.get(i), chunkIds.get(i)})
            .toList();
    jdbcTemplate.batchUpdate(UPDATE_EMBEDDING_SQL, batchArgs);
  }
}
