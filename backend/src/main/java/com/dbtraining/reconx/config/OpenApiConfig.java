package com.dbtraining.reconx.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI and Swagger configuration for ReconX.
 *
 * Ticket: TICKET-ADV058
 *
 * Provides:
 * 1. General ReconX API information
 * 2. JWT bearer authentication definition
 * 3. Public API documentation group
 * 4. Admin API documentation group
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configures the general OpenAPI document metadata and JWT security scheme.
     */
    @Bean
    public OpenAPI reconxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReconX API")
                        .description("Trade reconciliation platform — DB TDI 2026")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("ReconX Team")
                                .email("reconx-team@dbtraining.com")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth")
                );
    }

    /**
     * Public-facing API documentation.
     *
     * Contains trade and reconciliation endpoints only.
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch(
                        "/v1/trades/**",
                        "/v1/recon/**"
                )
                .build();
    }

    /**
     * Internal/admin API documentation.
     *
     * Contains administration and actuator endpoints.
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch(
                        "/v1/admin/**",
                        "/actuator/**"
                )
                .build();
    }
}