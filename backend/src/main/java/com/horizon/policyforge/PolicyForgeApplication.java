package com.horizon.policyforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point. Layers: web, service, repository, domain, config (see docs/architecture.md). */
@SpringBootApplication
public class PolicyForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyForgeApplication.class, args);
    }
}
