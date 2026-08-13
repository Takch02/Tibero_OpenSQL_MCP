package com.test_mcp.tibero_mcp.ingestion;

record IngestionTaskClaim(Long taskId, Long documentId, Integer documentVersion, String workerId) {}
