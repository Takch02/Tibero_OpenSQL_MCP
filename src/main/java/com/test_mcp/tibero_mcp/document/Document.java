package com.test_mcp.tibero_mcp.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "document")
@Getter
@NoArgsConstructor
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(columnDefinition = "vector(3)")
  @ColumnTransformer(write = "?::vector")
  private String embedding;

  public Document(String title, String content, String embedding) {
    this.title = title;
    this.content = content;
    this.embedding = embedding;
  }
}
