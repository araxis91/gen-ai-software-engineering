package com.homework6.pipeline.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the REST API Gateway (specification-capstone.md Task 2).
 * A second, independent {@code main} alongside {@code Integrator.main} -- both remain
 * valid, independently runnable ways to run this project (CLI vs. HTTP).
 */
@SpringBootApplication
public class PipelineApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineApiApplication.class, args);
    }
}
