package com.test_mcp.tibero_mcp.ingestion;

import com.test_mcp.tibero_mcp.exception.InvalidRequestException;
import com.test_mcp.tibero_mcp.ingestion.dto.DocumentResponse;
import com.test_mcp.tibero_mcp.ingestion.dto.DocumentVersionResponse;
import com.test_mcp.tibero_mcp.ingestion.dto.IngestionStatusResponse;
import com.test_mcp.tibero_mcp.ingestion.dto.RestoreDocumentRequest;
import com.test_mcp.tibero_mcp.ingestion.dto.UpdateDocumentRequest;
import com.test_mcp.tibero_mcp.ingestion.dto.UploadRequest;
import com.test_mcp.tibero_mcp.ingestion.dto.UploadResponse;
import com.test_mcp.tibero_mcp.ingestion.entity.Document;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Ingestion", description = "문서 업로드 API")
public class IngestionController {

  private final IngestionService ingestionService;

  @PostMapping
  @Operation(
      summary = "문서 업로드",
      description =
          "idempotencyKey 기준으로 멱등하게 문서를 업로드하고 청킹한다. 임베딩은 EmbeddingWorker가 비동기로 처리한다(응답 시점엔 status=PENDING).")
  @ApiResponse(responseCode = "200", description = "업로드 성공(기존 문서 재요청 시 기존 문서 반환)")
  public UploadResponse upload(@RequestBody UploadRequest request) {
    if (!StringUtils.hasText(request.idempotencyKey())
        || !StringUtils.hasText(request.content())
        || !StringUtils.hasText(request.ownerId())) {
      throw new InvalidRequestException("idempotencyKey/content/ownerId는 필수입니다.");
    }
    Document document =
        ingestionService.upload(
            request.idempotencyKey(),
            request.title(),
            request.content(),
            request.ownerId(),
            request.category());
    return UploadResponse.from(document);
  }

  @GetMapping("/{documentId}")
  @Operation(summary = "문서 현재 상태 조회", description = "소유자만 최신 작성 버전과 검색 노출 버전을 조회한다.")
  @ApiResponse(responseCode = "200", description = "문서 조회 성공")
  public DocumentResponse getDocument(@PathVariable Long documentId, @RequestParam String ownerId) {
    validateOwnerId(ownerId);
    return DocumentResponse.from(ingestionService.getDocument(documentId, ownerId));
  }

  @GetMapping("/{documentId}/ingestion")
  @Operation(
      summary = "문서 인제스천 상태 조회",
      description = "소유자만 최신 버전의 Outbox 상태, 재시도 정보, 청크 임베딩 진행률을 조회한다.")
  @ApiResponse(responseCode = "200", description = "인제스천 상태 조회 성공")
  public IngestionStatusResponse getIngestionStatus(
      @PathVariable Long documentId, @RequestParam String ownerId) {
    validateOwnerId(ownerId);
    return ingestionService.getIngestionStatus(documentId, ownerId);
  }

  @GetMapping("/{documentId}/versions")
  @Operation(summary = "문서 버전 이력 조회", description = "소유자만 과거 원문과 버전별 임베딩 상태를 조회한다.")
  @ApiResponse(responseCode = "200", description = "버전 이력 조회 성공")
  public java.util.List<DocumentVersionResponse> getVersions(
      @PathVariable Long documentId, @RequestParam String ownerId) {
    validateOwnerId(ownerId);
    return ingestionService.getVersions(documentId, ownerId).stream()
        .map(DocumentVersionResponse::from)
        .toList();
  }

  @PutMapping("/{documentId}")
  @Operation(
      summary = "문서 새 버전 업로드",
      description = "expectedVersion이 현재 버전과 일치할 때만 새 버전을 만든다. 새 버전이 임베딩될 때까지 이전 정상 버전이 검색된다.")
  @ApiResponse(responseCode = "200", description = "새 버전 생성 성공")
  public DocumentResponse update(
      @PathVariable Long documentId, @RequestBody UpdateDocumentRequest request) {
    if (!StringUtils.hasText(request.ownerId())
        || !StringUtils.hasText(request.content())
        || request.expectedVersion() == null
        || request.expectedVersion() < 1) {
      throw new InvalidRequestException("ownerId/content/expectedVersion(1 이상)은 필수입니다.");
    }
    return DocumentResponse.from(
        ingestionService.update(
            documentId,
            request.ownerId(),
            request.expectedVersion(),
            request.title(),
            request.content(),
            request.category()));
  }

  @DeleteMapping("/{documentId}")
  @Operation(summary = "문서 논리 삭제", description = "문서를 검색에서 즉시 제외하고 버전 이력은 감사·복구용으로 보존한다.")
  @ApiResponse(responseCode = "204", description = "문서 삭제 성공")
  public org.springframework.http.ResponseEntity<Void> delete(
      @PathVariable Long documentId,
      @RequestParam String ownerId,
      @RequestParam Integer expectedVersion) {
    validateOwnerId(ownerId);
    if (expectedVersion == null || expectedVersion < 1) {
      throw new InvalidRequestException("expectedVersion은 1 이상이어야 합니다.");
    }
    ingestionService.delete(documentId, ownerId, expectedVersion);
    return org.springframework.http.ResponseEntity.noContent().build();
  }

  @PostMapping("/{documentId}/versions/{version}/restore")
  @Operation(
      summary = "과거 버전으로 문서 복원",
      description = "삭제된 문서를 지정한 과거 원문으로 새 버전으로 복원한다. 기존 버전은 변경하지 않는다.")
  @ApiResponse(responseCode = "200", description = "문서 복원 성공")
  public DocumentResponse restore(
      @PathVariable Long documentId,
      @PathVariable Integer version,
      @RequestBody RestoreDocumentRequest request) {
    if (!StringUtils.hasText(request.ownerId())
        || request.expectedVersion() == null
        || request.expectedVersion() < 1
        || version < 1) {
      throw new InvalidRequestException("ownerId/expectedVersion/source version(1 이상)은 필수입니다.");
    }
    return DocumentResponse.from(
        ingestionService.restore(
            documentId, request.ownerId(), request.expectedVersion(), version));
  }

  private static void validateOwnerId(String ownerId) {
    if (!StringUtils.hasText(ownerId)) {
      throw new InvalidRequestException("ownerId는 필수입니다.");
    }
  }
}
