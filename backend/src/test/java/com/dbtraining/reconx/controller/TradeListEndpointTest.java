package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.TradeResponse;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV057 — the list endpoint over real HTTP, with rows in the table.
 *
 * Deliberately NOT @Transactional: a transactional test keeps the Hibernate
 * session open for the whole method, which would hide a
 * LazyInitializationException in mapper.toResponse(...) — the entity's
 * instrument/counterparty are @ManyToOne(fetch = LAZY) and the app runs with
 * spring.jpa.open-in-view=false, so the mapper must see initialised
 * associations on its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
// Own in-memory database — see the note in TradeListPaginationTest.
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:reconx-endpoint;MODE=PostgreSQL;"
      + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=USER")
class TradeListEndpointTest {

    @Autowired TestRestTemplate rest;
    @Autowired TradeRepository tradeRepo;
    @Autowired InstrumentRepository instRepo;
    @Autowired CounterpartyRepository cpRepo;

    @BeforeEach
    void seed() {
        tradeRepo.deleteAll();
        var inst = instRepo.findAll().getFirst();
        var cp = cpRepo.findAll().getFirst();

        for (int i = 0; i < 3; i++) {
            Trade t = new Trade();
            t.setTradeRef("HTTP-%d".formatted(i));
            t.setInstrument(inst);
            t.setCounterparty(cp);
            t.setAssetClass("EQUITY");
            t.setSide("BUY");
            t.setQuantity(BigDecimal.valueOf(50 + i));
            t.setPrice(BigDecimal.valueOf(12));
            t.setTradeDate(LocalDate.of(2026, 3, 1).plusDays(i));
            t.setStatus("PENDING");
            tradeRepo.save(t);
        }
    }

    @AfterEach
    void cleanup() {
        tradeRepo.deleteAll();
    }

    private PagedResponse<TradeResponse> get(String url) {
        var res = rest.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<PagedResponse<TradeResponse>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    @Test
    void returnsMappedItems_withoutLazyInitialisationError() {
        var body = get("/v1/trades?page=0&size=10");

        assertThat(body).isNotNull();
        assertThat(body.totalElements()).isEqualTo(3);
        assertThat(body.items()).hasSize(3);

        // The lazy associations must have been resolved by the mapper.
        assertThat(body.items()).allSatisfy(item -> {
            assertThat(item.instrumentId()).isNotNull();
            assertThat(item.instrumentSymbol()).isNotBlank();
            assertThat(item.counterpartyId()).isNotNull();
            assertThat(item.counterpartyName()).isNotBlank();
            assertThat(item.tradeRef()).startsWith("HTTP-");
            assertThat(item.status()).isEqualTo("PENDING");
        });
    }

    @Test
    void paginatesOverHttp() {
        var first = get("/v1/trades?page=0&size=2");
        assertThat(first.items()).hasSize(2);
        assertThat(first.totalPages()).isEqualTo(2);

        var second = get("/v1/trades?page=1&size=2");
        assertThat(second.items()).hasSize(1);
        assertThat(second.page()).isEqualTo(1);
    }

    @Test
    void defaultSortIsTradeDateDesc() {
        var body = get("/v1/trades");

        assertThat(body.size()).isEqualTo(20);   // @PageableDefault
        assertThat(body.items().getFirst().tradeDate()).isEqualTo(LocalDate.of(2026, 3, 3));
    }

    @Test
    void filtersApplyOverHttp() {
        assertThat(get("/v1/trades?status=SETTLED").totalElements()).isZero();
        assertThat(get("/v1/trades?from=2026-03-02&to=2026-03-03").totalElements()).isEqualTo(2);
    }
}
