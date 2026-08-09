package com.test_mcp.tibero_mcp.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문서 업로드 요청")
public record UploadRequest(
    @Schema(description = "재시도 시 중복 생성을 막는 멱등키", requiredMode = Schema.RequiredMode.REQUIRED)
        String idempotencyKey,
    @Schema(description = "문서 제목") String title,
    @Schema(description = "문서 본문(텍스트)", requiredMode = Schema.RequiredMode.REQUIRED) String content,
    @Schema(
            description = "문서 소유자 ID. 검색 시 이 값으로 접근 권한을 판별한다.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String ownerId,
    @Schema(description = "문서 분류(선택, 검색 필터로 사용)") String category) {}
