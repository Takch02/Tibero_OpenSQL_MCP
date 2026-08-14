package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class MultipartUploadSizeIntegrationTest {

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

  @Autowired TestRestTemplate restTemplate;

  @Autowired JdbcTemplate jdbcTemplate;

  @Autowired DocumentRepository documentRepository;

  @LocalServerPort int port;

  @Value("${app.document-upload.max-file-size-bytes}")
  int maxFileSizeBytes;

  @BeforeEach
  void clearDatabase() {
    jdbcTemplate.update("DELETE FROM ingestion_log");
    jdbcTemplate.update("DELETE FROM document_chunks");
    jdbcTemplate.update("DELETE FROM ingestion_tasks");
    jdbcTemplate.update("DELETE FROM document_versions");
    jdbcTemplate.update("DELETE FROM documents");
  }

  @Test
  void 파일_상한과_같은_PDF는_multipart_오버헤드가_있어도_업로드된다() throws IOException {
    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(MediaType.APPLICATION_PDF);
    ByteArrayResource file =
        new ByteArrayResource(paddedPdfAtFileLimit()) {
          @Override
          public String getFilename() {
            return "boundary.pdf";
          }
        };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", new HttpEntity<>(file, fileHeaders));
    body.add("idempotencyKey", "multipart-size-boundary");
    body.add("ownerId", "user-1");

    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
    var response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/documents/files",
            new HttpEntity<>(body, requestHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(documentRepository.count()).isOne();
  }

  // 설정값 크기만큼 pdf 용량을 만듦
  private byte[] paddedPdfAtFileLimit() throws IOException {
    byte[] pdf = fixtureBytes("sample-document.pdf");
    if (pdf.length > maxFileSizeBytes) {
      throw new IllegalStateException("PDF fixture가 파일 상한보다 큽니다.");
    }
    byte[] padded = new byte[maxFileSizeBytes];
    Arrays.fill(padded, (byte) ' ');
    System.arraycopy(pdf, 0, padded, 0, pdf.length);
    return padded;
  }

  private static byte[] fixtureBytes(String filename) throws IOException {
    try (var inputStream = new ClassPathResource("fixtures/" + filename).getInputStream()) {
      return inputStream.readAllBytes();
    }
  }
}
