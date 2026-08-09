package com.test_mcp.tibero_mcp.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "삭제된 문서를 과거 원문으로 복원하는 요청")
public record RestoreDocumentRequest(
    @Schema(description = "문서 소유자 ID", requiredMode = Schema.RequiredMode.REQUIRED) String ownerId,
    @Schema(description = "복원 전 현재 버전", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer expectedVersion) {}
