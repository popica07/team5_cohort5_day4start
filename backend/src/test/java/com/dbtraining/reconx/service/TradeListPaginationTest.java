package com.dbtraining.reconx.service;

import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV057 — pagination / Page<T> on the trade list query.
 *
 * The Liquibase seed ships counterparties, instruments and users but no trades,
 * so each test inserts its own rows and drives TradeService.list() — the same
 * path TradeController.list() delegates to.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
// Own in-memory database: the dev profile's jdbc:h2:mem:reconx is a *named*
// H2 instance shared JVM-wide, so a second @SpringBootTest context would run
// Liquibase over the same schema and fail on DATABASECHANGELOG.
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:reconx-pagination;MODE=PostgreSQL;"
      + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=USER")
class TradeListPaginationTest {

    @Autowired TradeService service;
    @Autowired TradeRepository tradeRepo;
    @Autowired InstrumentRepository instRepo;
    @Autowired CounterpartyRepository cpRepo;

    private Counterparty cpA;
    private Counterparty cpB;

    @BeforeEach
    void seedTrades() {
        tradeRepo.deleteAll();

        List<Counterparty> cps = cpRepo.findAll();
        List<Instrument> insts = instRepo.findAll();
        assertThat(cps).hasSizeGreaterThanOrEqualTo(2);
        assertThat(insts).isNotEmpty();

        cpA = cps.get(0);
        cpB = cps.get(1);
        Instrument inst = insts.get(0);

        // 25 trades on ascending dates; alternating counterparty, every 5th SETTLED.
        for (int i = 0; i < 25; i++) {
            Trade t = new Trade();
            t.setTradeRef("T-%03d".formatted(i));
            t.setInstrument(inst);
            t.setCounterparty(i % 2 == 0 ? cpA : cpB);
            t.setAssetClass("EQUITY");
            t.setSide("BUY");
            t.setQuantity(BigDecimal.valueOf(100 + i));
            t.setPrice(BigDecimal.valueOf(10));
            t.setTradeDate(LocalDate.of(2026, 1, 1).plusDays(i));
            t.setStatus(i % 5 == 0 ? "SETTLED" : "PENDING");
            tradeRepo.save(t);
        }
        tradeRepo.flush();
    }

    private PageRequest paged(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "tradeDate"));
    }

    @Test
    void firstPage_respectsSizeAndReportsTotals() {
        var page = service.list(null, null, null, null, paged(0, 5));

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(5);
    }

    @Test
    void lastPage_isPartialWhenNotDivisible() {
        var page = service.list(null, null, null, null, paged(0, 10));
        assertThat(page.getTotalPages()).isEqualTo(3);

        var last = service.list(null, null, null, null, paged(2, 10));
        assertThat(last.getContent()).hasSize(5);
        assertThat(last.isLast()).isTrue();
    }

    @Test
    void sortByTradeDateDesc_isApplied() {
        var page = service.list(null, null, null, null, paged(0, 5));

        var dates = page.getContent().stream().map(Trade::getTradeDate).toList();
        assertThat(dates).isSortedAccordingTo((a, b) -> b.compareTo(a));
        // newest of the 25 seeded trades is 2026-01-01 + 24 days
        assertThat(dates.getFirst()).isEqualTo(LocalDate.of(2026, 1, 25));
    }

    @Test
    void pagesDoNotOverlap() {
        var p0 = service.list(null, null, null, null, paged(0, 10));
        var p1 = service.list(null, null, null, null, paged(1, 10));

        var refs0 = p0.getContent().stream().map(Trade::getTradeRef).toList();
        var refs1 = p1.getContent().stream().map(Trade::getTradeRef).toList();
        assertThat(refs0).doesNotContainAnyElementsOf(refs1);
    }

    @Test
    void statusFilter_narrowsTotalElements() {
        var page = service.list(null, null, "SETTLED", null, paged(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(5);  // i % 5 == 0 → 0,5,10,15,20
        assertThat(page.getContent()).allMatch(t -> t.getStatus().equals("SETTLED"));
    }

    @Test
    void dateRangeFilter_isInclusiveOnBothBounds() {
        var from = LocalDate.of(2026, 1, 5);
        var to   = LocalDate.of(2026, 1, 9);

        var page = service.list(from, to, null, null, paged(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent())
                .allMatch(t -> !t.getTradeDate().isBefore(from) && !t.getTradeDate().isAfter(to));
    }

    @Test
    void counterpartyFilter_usesNestedIdPath() {
        var page = service.list(null, null, null, cpA.getId(), paged(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(13);  // even indices 0..24
        assertThat(page.getContent()).allMatch(t -> t.getCounterparty().getId().equals(cpA.getId()));
    }

    @Test
    void filtersCompose_andNullsMeanNoConstraint() {
        var both = service.list(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11),
                "SETTLED", cpA.getId(), paged(0, 20));

        // days 1..11 → indices 0..10; SETTLED → 0,5,10; of those only the even
        // indices 0 and 10 sit on cpA (5 is odd → cpB), so all three filters
        // intersecting leaves 2.
        assertThat(both.getTotalElements()).isEqualTo(2);

        var unfiltered = service.list(null, null, null, null, paged(0, 100));
        assertThat(unfiltered.getTotalElements()).isEqualTo(25);
    }
}
