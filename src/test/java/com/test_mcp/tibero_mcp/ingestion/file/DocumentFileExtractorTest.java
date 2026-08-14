package com.test_mcp.tibero_mcp.ingestion.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.test_mcp.tibero_mcp.exception.ErrorCode;
import com.test_mcp.tibero_mcp.exception.FileUploadException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

class DocumentFileExtractorTest {

  private final DocumentFileExtractor extractor =
      new DocumentFileExtractor(10 * 1024 * 1024, 2_000_000);

  @Test
  void UTF8_TXT에서_본문과_파일_메타데이터를_추출한다() throws IOException {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "sample-document.txt",
            "text/plain;charset=UTF-8",
            fixtureBytes("sample-document.txt"));

    ExtractedFile extracted = extractor.extract(file);

    assertThat(extracted.filename()).isEqualTo("sample-document.txt");
    assertThat(extracted.contentType()).isEqualTo("text/plain");
    assertThat(extracted.content()).contains("테스트");
    assertThat(extracted.fileHash()).hasSize(64);
  }

  @Test
  void PDF에서_본문을_추출한다() throws IOException {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "sample-document.pdf", "application/pdf", fixtureBytes("sample-document.pdf"));

    ExtractedFile extracted = extractor.extract(file);

    assertThat(extracted.content()).isNotBlank();
    assertThat(extracted.fileHash()).hasSize(64);
  }

  @Test
  void 확장자와_MIME이_맞지_않으면_거절한다() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "policy.pdf", "text/plain", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

    assertThatThrownBy(() -> extractor.extract(file))
        .isInstanceOf(FileUploadException.class)
        .extracting(error -> ((FileUploadException) error).getErrorCode())
        .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
  }

  @Test
  void UTF8이_아닌_TXT는_거절한다() {
    MockMultipartFile file =
        new MockMultipartFile("file", "policy.txt", "text/plain", new byte[] {(byte) 0xFF});

    assertThatThrownBy(() -> extractor.extract(file))
        .isInstanceOf(FileUploadException.class)
        .extracting(error -> ((FileUploadException) error).getErrorCode())
        .isEqualTo(ErrorCode.FILE_EXTRACTION_FAILED);
  }

  @Test
  void 파일_크기_상한을_초과하면_본문을_읽기_전에_거절한다() {
    DocumentFileExtractor smallLimitExtractor = new DocumentFileExtractor(3, 100);
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "large.txt", "text/plain", "four".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> smallLimitExtractor.extract(file))
        .isInstanceOf(FileUploadException.class)
        .extracting(error -> ((FileUploadException) error).getErrorCode())
        .isEqualTo(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED);
  }

  @Test
  void 빈_문서와_손상된_PDF는_거절한다() {
    MockMultipartFile emptyText =
        new MockMultipartFile(
            "file", "empty.txt", "text/plain", "  ".getBytes(StandardCharsets.UTF_8));
    MockMultipartFile invalidPdf =
        new MockMultipartFile(
            "file",
            "broken.pdf",
            "application/pdf",
            "%PDF-broken".getBytes(StandardCharsets.US_ASCII));

    assertThatThrownBy(() -> extractor.extract(emptyText))
        .isInstanceOf(FileUploadException.class)
        .extracting(error -> ((FileUploadException) error).getErrorCode())
        .isEqualTo(ErrorCode.EMPTY_DOCUMENT);
    assertThatThrownBy(() -> extractor.extract(invalidPdf))
        .isInstanceOf(FileUploadException.class)
        .extracting(error -> ((FileUploadException) error).getErrorCode())
        .isEqualTo(ErrorCode.FILE_EXTRACTION_FAILED);
  }

  @Test
  void 암호화된_PDF는_거절한다() throws IOException {
    MockMultipartFile file =
        new MockMultipartFile("file", "locked.pdf", "application/pdf", encryptedPdf());

    assertThatThrownBy(() -> extractor.extract(file))
        .isInstanceOf(FileUploadException.class)
        .extracting(error -> ((FileUploadException) error).getErrorCode())
        .isEqualTo(ErrorCode.FILE_EXTRACTION_FAILED);
  }

  private static byte[] fixtureBytes(String filename) throws IOException {
    try (var inputStream = new ClassPathResource("fixtures/" + filename).getInputStream()) {
      return inputStream.readAllBytes();
    }
  }

  private static byte[] encryptedPdf() throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage());
      document.protect(
          new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
      document.save(output);
      return output.toByteArray();
    }
  }
}
