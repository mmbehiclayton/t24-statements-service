package com.sensys.statement;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "statement")
public record StatementProperties(
        String defaultCompany,
        String outputDir,
        String bankName,
        int batchThreads,
        int batchMaxAccounts) {
}
