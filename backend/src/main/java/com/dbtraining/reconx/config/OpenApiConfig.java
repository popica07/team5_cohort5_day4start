package com.dbtraining.reconx.config;

import org.springframework.context.annotation.Configuration;

/**
 * ============================================================================
 * OpenApiConfig — TICKET-ADV058
 * ============================================================================
 * WHAT:    Customises the OpenAPI document Springdoc generates (title, version,
 *          description, contact + bearerAuth security scheme).
 * HOW:     Single @Bean of type io.swagger.v3.oas.models.OpenAPI.
 * WHY:     Swagger UI on /api/swagger-ui.html becomes the single source of
 *          truth for the API contract — front-end and QA teams read it
 *          instead of digging through controllers.
 * OBSERVE: After wiring, the title in the top-left of Swagger UI is
 *          "ReconX API" and a green "Authorize" button accepts bearer JWTs.
 * ============================================================================
 *
 *  TODO(TICKET-ADV058):
 *    @Bean
 *    public OpenAPI reconxOpenAPI() {
 *        return new OpenAPI()
 *            .info(new Info()
 *                .title("ReconX API")
 *                .version("v1")
 *                .description("Enterprise Trade Reconciliation Platform (Advanced Track)")
 *                .contact(new Contact().name("DB TDI Training").email("tdi@db.com")))
 *            .components(new Components().addSecuritySchemes("bearerAuth",
 *                new SecurityScheme()
 *                    .type(SecurityScheme.Type.HTTP)
 *                    .scheme("bearer")
 *                    .bearerFormat("JWT")))
 *            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
 *    }
 *
 *  HINT: Without this bean Springdoc still produces a default OpenAPI doc —
 *        you'll see Swagger UI work, but with generic metadata and no
 *        "Authorize" button.
 *
 *  Provides:
 *  1. General ReconX API information
 *  2. JWT bearer authentication definition
 *  3. Public API documentation group
 *  4. Admin API documentation group
 * ============================================================================
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
