package com.test_mcp.tibero_mcp.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class McpToolConfig {

  private final SearchMcpTools searchMcpTools;

  @Bean
  public ToolCallbackProvider searchToolCallbacks() {
    return MethodToolCallbackProvider.builder().toolObjects(searchMcpTools).build();
  }
}
