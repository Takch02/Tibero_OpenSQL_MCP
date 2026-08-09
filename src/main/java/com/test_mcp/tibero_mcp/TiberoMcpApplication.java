package com.test_mcp.tibero_mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TiberoMcpApplication {

  public static void main(String[] args) {
    SpringApplication.run(TiberoMcpApplication.class, args);
  }
}
