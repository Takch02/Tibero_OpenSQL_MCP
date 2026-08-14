package com.test_mcp.tibero_mcp.ingestion.file;

import com.test_mcp.tibero_mcp.exception.ErrorCode;
import com.test_mcp.tibero_mcp.exception.FileUploadException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
// multipart 파일의 형식 검증과 텍스트 추출을 한 곳에 모아 Controller가 파일 파서를 직접 알지 않게 한다.
public class DocumentFileExtractor {

  private static final String PDF_CONTENT_TYPE = "application/pdf";
  private static final String TEXT_CONTENT_TYPE = "text/plain";

  private final long maxFileSizeBytes;
  private final int maxExtractedCharacters;

  public DocumentFileExtractor(
      @Value("${app.document-upload.max-file-size-bytes}") long maxFileSizeBytes,
      @Value("${app.document-upload.max-extracted-characters}") int maxExtractedCharacters) {
    this.maxFileSizeBytes = maxFileSizeBytes;
    this.maxExtractedCharacters = maxExtractedCharacters;
  }

  public ExtractedFile extract(MultipartFile file) {
    validateSize(file);
    String filename = normalizedFilename(file.getOriginalFilename());
    String contentType = normalizeContentType(file.getContentType());
    byte[] bytes = readBytes(file);

    String content =
        switch (fileType(filename, contentType, bytes)) {
          case PDF -> extractPdf(bytes);
          case TEXT -> extractUtf8Text(bytes);
        };
    validateExtractedContent(content);
    return new ExtractedFile(filename, contentType, bytes.length, sha256(bytes), content);
  }

  private void validateSize(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new FileUploadException(ErrorCode.EMPTY_DOCUMENT, "비어 있는 파일은 업로드할 수 없습니다.");
    }
    if (file.getSize() > maxFileSizeBytes) {
      throw new FileUploadException(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED, "파일 크기는 10 MiB 이하여야 합니다.");
    }
  }

  private static String normalizedFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new FileUploadException(ErrorCode.INVALID_REQUEST, "파일명은 필수입니다.");
    }
    String filename = originalFilename.replace('\\', '/');
    filename = filename.substring(filename.lastIndexOf('/') + 1);
    if (filename.isBlank() || filename.length() > 255) {
      throw new FileUploadException(ErrorCode.INVALID_REQUEST, "파일명이 올바르지 않습니다.");
    }
    return filename;
  }

  private static String normalizeContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      throw new FileUploadException(ErrorCode.UNSUPPORTED_FILE_TYPE, "PDF 또는 UTF-8 TXT 파일만 지원합니다.");
    }
    String normalized = contentType.toLowerCase(Locale.ROOT);
    int parameterStart = normalized.indexOf(';');
    return (parameterStart < 0 ? normalized : normalized.substring(0, parameterStart)).trim();
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      // 최대 10 MiB로 제한된 요청만 읽으므로 추출 라이브러리에 전달할 바이트 배열 크기는 상한이 있다.
      return file.getBytes();
    } catch (IOException e) {
      throw new FileUploadException(ErrorCode.FILE_EXTRACTION_FAILED, "파일을 읽을 수 없습니다.");
    }
  }

  private static FileType fileType(String filename, String contentType, byte[] bytes) {
    String lowercaseName = filename.toLowerCase(Locale.ROOT);
    if (lowercaseName.endsWith(".pdf")
        && PDF_CONTENT_TYPE.equals(contentType)
        && hasPdfHeader(bytes)) {
      return FileType.PDF;
    }
    if (lowercaseName.endsWith(".txt") && TEXT_CONTENT_TYPE.equals(contentType)) {
      return FileType.TEXT;
    }
    throw new FileUploadException(ErrorCode.UNSUPPORTED_FILE_TYPE, "PDF 또는 UTF-8 TXT 파일만 지원합니다.");
  }

  private static boolean hasPdfHeader(byte[] bytes) {
    return bytes.length >= 5
        && bytes[0] == '%'
        && bytes[1] == 'P'
        && bytes[2] == 'D'
        && bytes[3] == 'F'
        && bytes[4] == '-';
  }

  private String extractPdf(byte[] bytes) {
    try (PDDocument document = Loader.loadPDF(bytes)) {
      if (document.isEncrypted()) {
        throw new FileUploadException(ErrorCode.FILE_EXTRACTION_FAILED, "암호화된 PDF는 지원하지 않습니다.");
      }
      return new PDFTextStripper().getText(document);
    } catch (FileUploadException e) {
      throw e;
    } catch (InvalidPasswordException e) {
      throw new FileUploadException(ErrorCode.FILE_EXTRACTION_FAILED, "암호화된 PDF는 지원하지 않습니다.");
    } catch (IOException e) {
      throw new FileUploadException(ErrorCode.FILE_EXTRACTION_FAILED, "손상된 PDF는 업로드할 수 없습니다.");
    }
  }

  private static String extractUtf8Text(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new FileUploadException(ErrorCode.FILE_EXTRACTION_FAILED, "TXT 파일은 UTF-8 인코딩이어야 합니다.");
    }
  }

  private void validateExtractedContent(String content) {
    if (content.isBlank()) {
      throw new FileUploadException(ErrorCode.EMPTY_DOCUMENT, "추출된 문서 내용이 비어 있습니다.");
    }
    if (content.length() > maxExtractedCharacters) {
      throw new FileUploadException(
          ErrorCode.FILE_SIZE_LIMIT_EXCEEDED, "추출된 문서 내용이 최대 길이를 초과했습니다.");
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
    }
  }

  private enum FileType {
    PDF,
    TEXT
  }
}
