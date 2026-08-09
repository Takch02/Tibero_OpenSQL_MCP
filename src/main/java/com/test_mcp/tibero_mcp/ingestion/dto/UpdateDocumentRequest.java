package com.test_mcp.tibero_mcp.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문서 새 버전 업로드 요청")
public record UpdateDocumentRequest(
    @Schema(description = "문서 소유자 ID", requiredMode = Schema.RequiredMode.REQUIRED) String ownerId,
    @Schema(description = "수정의 기준이 되는 현재 버전", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer expectedVersion,
    @Schema(description = "새 문서 제목") String title,
    @Schema(description = "새 문서 본문", requiredMode = Schema.RequiredMode.REQUIRED) String content,
    @Schema(description = "새 문서 분류") String category) {}
