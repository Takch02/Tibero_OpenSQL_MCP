package com.test_mcp.tibero_mcp.ingestion.chunking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.chunking")
public record ChunkingProperties(@DefaultValue("500") int size, @DefaultValue("50") int overlap) {}
