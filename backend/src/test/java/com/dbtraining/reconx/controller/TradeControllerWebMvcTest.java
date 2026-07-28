package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeMapper;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.repository.entity.Trade;
import com.dbtraining.reconx.security.JwtTokenProvider;
import com.dbtraining.reconx.security.SecurityConfig;
import com.dbtraining.reconx.service.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 * TICKET-ADV075 — MockMvc: authenticated TRADER create returns 201
 * TICKET-ADV076 — MockMvc: unauthenticated create returns 401
 * TICKET-ADV077 — MockMvc: authenticated VIEWER create returns 403
 *
 * A @WebMvcTest slice: no JPA, no DataSource, no Docker, no database. Every
 * layer below the controller is mocked, so these three methods pin the
 * 201/401/403 boundary of POST /v1/trades and nothing else.
 *
 * Two things about the slice that are easy to get wrong:
 *
 *  - The real SecurityConfig is @Configuration, which @WebMvcTest does NOT
 *    scan. Without the @Import below the slice would fall back to Spring
 *    Boot's default security and ADV077 would be asserting against a chain
 *    that has no RBAC matrix at all — a green test proving nothing.
 *
 *  - The path is "/v1/trades", not "/api/v1/trades". The /api prefix comes
 *    from server.servlet.context-path, a servlet-container concern MockMvc
 *    does not apply.
 * ============================================================================
 */
@WebMvcTest(TradeController.class)
@Import(SecurityConfig.class)
class TradeControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // TradeController constructor-injects both, so the slice needs both.
    @MockitoBean private TradeService tradeService;
    @MockitoBean private TradeMapper tradeMapper;

    // JwtAuthenticationFilter is a @Component Filter — @WebMvcTest pulls Filter
    // beans in, but not the plain @Component JwtTokenProvider it injects.
    // Without this the context fails before any test runs.
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    // ReconxApplication carries @EnableJpaAuditing, and @WebMvcTest uses that
    // class as its context configuration — so the slice tries to build
    // jpaAuditingHandler and dies on "JPA metamodel must not be empty",
    // because a web slice loads no entities. Standing in the mapping context
    // satisfies it without dragging JPA into the slice.
    @MockitoBean private JpaMetamodelMappingContext jpaMappingContext;

    /** tradeRef must match ^[A-Z]{3}-\d{8}-\d{4}$; status is set server-side. */
    private TradeRequest validRequest() {
        return new TradeRequest(
                "TRD-20260315-9999",
                1L,
                1L,
                "EQUITY",
                "BUY",
                new BigDecimal("100.0000"),
                new BigDecimal("245.50"),
                LocalDate.now());
    }

    /**
     * TradeService.create returns a Trade entity and the controller maps it via
     * TradeMapper, so both are stubbed. Trade exposes no setId, so the id the
     * Location header is built from is planted reflectively.
     */
    private void stubCreateReturningId42() {
        Trade saved = new Trade();
        ReflectionTestUtils.setField(saved, "id", 42L);
        saved.setTradeRef("TRD-20260315-9999");

        Instant now = Instant.now();
        when(tradeService.create(any(), any())).thenReturn(saved);
        when(tradeMapper.toResponse(any())).thenReturn(new TradeResponse(
                42L,
                "TRD-20260315-9999",
                1L,
                "SAP.DE",
                1L,
                "Apex Brokers Inc",
                "EQUITY",
                "BUY",
                new BigDecimal("100.0000"),
                new BigDecimal("245.50"),
                LocalDate.now(),
                "PENDING",
                now,
                now));
    }

    // ── TICKET-ADV075 ───────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "TRADER")
    void testCreateTrade_authenticated_returns201() throws Exception {
        stubCreateReturningId42();

        mockMvc.perform(post("/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/v1/trades/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.tradeRef").value("TRD-20260315-9999"));
    }

    // ── TICKET-ADV076 ───────────────────────────────────────────────────────
    @Test
    void testCreateTrade_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());

        // Rejected by the filter chain before the controller runs.
        verify(tradeService, never()).create(any(), any());
    }

    // ── TICKET-ADV077 ───────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "VIEWER")
    void testCreateTrade_viewerRole_returns403() throws Exception {
        mockMvc.perform(post("/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest()))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // Denied at the security layer, not by the controller.
        verify(tradeService, never()).create(any(), any());
    }
}
