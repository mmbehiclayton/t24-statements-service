package com.sensys.statement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StatementMvpApplication {
    public static void main(String[] args) {
        SpringApplication.run(StatementMvpApplication.class, args);
    }
}
