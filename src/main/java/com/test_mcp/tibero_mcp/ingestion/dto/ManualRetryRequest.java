package com.test_mcp.tibero_mcp.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최종 실패한 최신 문서 버전의 수동 재처리 요청")
public record ManualRetryRequest(
    @Schema(description = "문서 소유자 ID") String ownerId,
    @Schema(description = "클라이언트가 조회한 최신 문서 버전") Integer expectedVersion) {}
