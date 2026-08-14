package com.test_mcp.tibero_mcp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test_mcp.tibero_mcp.ingestion.entity.DocumentVersion;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentChunkRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.DocumentVersionRepository;
import com.test_mcp.tibero_mcp.ingestion.repository.IngestionTaskRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class IngestionControllerIntegrationTest {

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

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired MockMvc mockMvc;

  @Autowired DocumentRepository documentRepository;

  @Autowired DocumentVersionRepository documentVersionRepository;

  @Autowired DocumentChunkRepository documentChunkRepository;

  @Autowired IngestionTaskRepository ingestionTaskRepository;

  @Test
  void 문서를_업로드하면_PENDING_상태로_생성된다() throws Exception {
    String body =
        """
        {"idempotencyKey":"ctrl-key-1","title":"제목","content":"본문 내용입니다.","ownerId":"user-1","category":"docs"}
        """;

    mockMvc
        .perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId", notNullValue()))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void 같은_idempotencyKey로_재요청하면_기존_문서를_반환한다() throws Exception {
    String body =
        """
        {"idempotencyKey":"ctrl-key-2","title":"제목","content":"본문 내용입니다.","ownerId":"user-1"}
        """;

    String firstResponse =
        mockMvc
            .perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String secondResponse =
        mockMvc
            .perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(objectMapper.readTree(secondResponse).get("documentId"))
        .isEqualTo(objectMapper.readTree(firstResponse).get("documentId"));
  }

  @Test
  void 소유자는_최신_버전의_인제스천_상태를_조회한다() throws Exception {
    String body =
        """
        {"idempotencyKey":"ctrl-status-key","title":"제목","content":"본문 내용입니다.","ownerId":"user-1"}
        """;
    String uploadResponse =
        mockMvc
            .perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long documentId = objectMapper.readTree(uploadResponse).get("documentId").asLong();

    mockMvc
        .perform(
            get("/api/documents/{documentId}/ingestion", documentId).param("ownerId", "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value(documentId))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.currentSearchVersion").doesNotExist())
        .andExpect(jsonPath("$.documentStatus").value("PENDING"))
        .andExpect(jsonPath("$.taskStatus").value("PENDING"))
        .andExpect(jsonPath("$.attemptCount").value(0))
        .andExpect(jsonPath("$.chunkCount").value(1))
        .andExpect(jsonPath("$.embeddedChunkCount").value(0));
  }

  @Test
  void 다른_소유자는_인제스천_상태를_조회할_수_없다() throws Exception {
    String body =
        """
        {"idempotencyKey":"ctrl-status-owner-key","title":"제목","content":"본문 내용입니다.","ownerId":"user-1"}
        """;
    String uploadResponse =
        mockMvc
            .perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long documentId = objectMapper.readTree(uploadResponse).get("documentId").asLong();

    mockMvc
        .perform(
            get("/api/documents/{documentId}/ingestion", documentId).param("ownerId", "other-user"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
  }

  @Test
  void TXT_파일을_업로드하면_파일_메타데이터_청크와_Outbox_작업을_함께_생성한다() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "sample-document.txt", "text/plain", fixtureBytes("sample-document.txt"));

    String response =
        mockMvc
            .perform(
                multipart("/api/documents/files")
                    .file(file)
                    .param("idempotencyKey", "file-txt-key")
                    .param("ownerId", "user-1")
                    .param("category", "security"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    long documentId = objectMapper.readTree(response).get("documentId").asLong();
    assertThat(documentRepository.findById(documentId).orElseThrow().getTitle())
        .isEqualTo("sample-document");
    DocumentVersion version =
        documentVersionRepository.findByDocumentIdAndVersion(documentId, 1).orElseThrow();
    assertThat(version.getSourceFilename()).isEqualTo("sample-document.txt");
    assertThat(version.getSourceContentType()).isEqualTo("text/plain");
    assertThat(version.getSourceSizeBytes()).isEqualTo(file.getSize());
    assertThat(version.getSourceFileHash()).hasSize(64);
    assertThat(documentChunkRepository.countByDocumentIdAndDocumentVersion(documentId, 1))
        .isEqualTo(1);
    assertThat(ingestionTaskRepository.findByDocumentIdAndDocumentVersion(documentId, 1))
        .isPresent();
    mockMvc
        .perform(get("/api/documents/{documentId}/versions", documentId).param("ownerId", "user-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sourceFilename").value("sample-document.txt"))
        .andExpect(jsonPath("$[0].sourceContentType").value("text/plain"));
  }

  @Test
  void PDF_파일을_업로드하면_추출_원문으로_청크와_Outbox_작업을_생성한다() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "sample-document.pdf", "application/pdf", fixtureBytes("sample-document.pdf"));

    String response =
        mockMvc
            .perform(
                multipart("/api/documents/files")
                    .file(file)
                    .param("idempotencyKey", "file-pdf-key")
                    .param("ownerId", "user-1"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    long documentId = objectMapper.readTree(response).get("documentId").asLong();
    assertThat(
            documentVersionRepository
                .findByDocumentIdAndVersion(documentId, 1)
                .orElseThrow()
                .getContent())
        .isNotBlank();
    assertThat(documentChunkRepository.countByDocumentIdAndDocumentVersion(documentId, 1))
        .isEqualTo(1);
    assertThat(ingestionTaskRepository.findByDocumentIdAndDocumentVersion(documentId, 1))
        .isPresent();
  }

  @Test
  void 같은_멱등키의_파일_재요청은_기존_문서를_반환한다() throws Exception {
    MockMultipartFile first =
        new MockMultipartFile(
            "file", "same.txt", "text/plain", "같은 문서".getBytes(StandardCharsets.UTF_8));
    MockMultipartFile second =
        new MockMultipartFile(
            "file", "same.txt", "text/plain", "바뀐 본문".getBytes(StandardCharsets.UTF_8));

    String firstResponse = uploadFile(first, "file-idempotency-key");
    String secondResponse = uploadFile(second, "file-idempotency-key");

    assertThat(objectMapper.readTree(secondResponse).get("documentId"))
        .isEqualTo(objectMapper.readTree(firstResponse).get("documentId"));
    assertThat(documentRepository.count()).isEqualTo(1);
  }

  @Test
  void 파일_원문을_변경하면_새_버전과_새_Outbox_작업을_생성한다() throws Exception {
    long documentId =
        objectMapper
            .readTree(
                uploadFile(
                    new MockMultipartFile(
                        "file", "v1.txt", "text/plain", "첫 번째 정책".getBytes(StandardCharsets.UTF_8)),
                    "file-version-key"))
            .get("documentId")
            .asLong();
    MockMultipartFile updated =
        new MockMultipartFile(
            "file", "v2.txt", "text/plain", "두 번째 정책".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/documents/{documentId}/file", documentId)
                .file(updated)
                .param("ownerId", "user-1")
                .param("expectedVersion", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.title").value("v2"));

    DocumentVersion version =
        documentVersionRepository.findByDocumentIdAndVersion(documentId, 2).orElseThrow();
    assertThat(version.getContent()).isEqualTo("두 번째 정책");
    assertThat(version.getSourceFilename()).isEqualTo("v2.txt");
    assertThat(ingestionTaskRepository.findByDocumentIdAndDocumentVersion(documentId, 2))
        .isPresent();
  }

  @Test
  void 손상된_파일은_DB에_부분_데이터를_남기지_않는다() throws Exception {
    long documentCount = documentRepository.count();
    long taskCount = ingestionTaskRepository.count();
    long chunkCount = documentChunkRepository.count();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "broken.pdf",
            "application/pdf",
            "%PDF-broken".getBytes(StandardCharsets.US_ASCII));

    mockMvc
        .perform(
            multipart("/api/documents/files")
                .file(file)
                .param("idempotencyKey", "broken-file-key")
                .param("ownerId", "user-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FILE_EXTRACTION_FAILED"));

    assertThat(documentRepository.count()).isEqualTo(documentCount);
    assertThat(ingestionTaskRepository.count()).isEqualTo(taskCount);
    assertThat(documentChunkRepository.count()).isEqualTo(chunkCount);
  }

  private String uploadFile(MockMultipartFile file, String idempotencyKey) throws Exception {
    return mockMvc
        .perform(
            multipart("/api/documents/files")
                .file(file)
                .param("idempotencyKey", idempotencyKey)
                .param("ownerId", "user-1"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static byte[] fixtureBytes(String filename) throws IOException {
    try (var inputStream = new ClassPathResource("fixtures/" + filename).getInputStream()) {
      return inputStream.readAllBytes();
    }
  }
}
