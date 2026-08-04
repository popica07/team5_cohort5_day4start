package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * TICKET-ADV055 — Custom JPQL filter query
 * TICKET-ADV056 — Specification-based dynamic queries (JpaSpecificationExecutor)
 * TICKET-ADV057 — Pageable / Page<T> for paginated list endpoints
 * ============================================================================
 */
public interface TradeRepository
        extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    /**
     * TICKET-ADV057 — the paged list endpoint maps each Trade through
     * TradeMapper *after* the service transaction has closed, and the app runs
     * with spring.jpa.open-in-view=false. Without this graph the LAZY
     * instrument/counterparty proxies are detached by then and the mapper
     * blows up with LazyInitializationException (HTTP 500). Fetching both
     * @ManyToOne sides here also removes the N+1 the mapper would otherwise
     * trigger — one query per row for the symbol and the name.
     */
    @Override
    @EntityGraph(attributePaths = {"instrument", "counterparty"})
    Page<Trade> findAll(Specification<Trade> spec, Pageable pageable);

    Optional<Trade> findByTradeRef(String tradeRef);

    @Query("""
        SELECT t FROM Trade t
        WHERE t.tradeDate BETWEEN :from AND :to
          AND (:status IS NULL OR t.status = :status)
        """)
    Page<Trade> findByFilters(@Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("status") String status,
                              Pageable pageable);

    long countByStatus(String status);

    /**
     * TICKET-ADV131 — trades per calendar month, aggregated in SQL.
     *
     * Returns one row per month that actually has trades, as
     * {@code [monthNumber, count]}; months with no trades are simply absent
     * and the service pads them to zero. Both columns come back as some
     * {@link Number} subtype — H2 and PostgreSQL disagree on the exact type
     * EXTRACT yields — so callers must widen rather than cast to Integer.
     *
     * The entity's @SQLRestriction applies here too, so soft-deleted trades
     * are excluded from the counts.
     */
    @Query("""
        SELECT EXTRACT(MONTH FROM t.tradeDate), COUNT(t)
        FROM Trade t
        WHERE t.tradeDate BETWEEN :from AND :to
        GROUP BY EXTRACT(MONTH FROM t.tradeDate)
        ORDER BY EXTRACT(MONTH FROM t.tradeDate)
        """)
    List<Object[]> countByMonthBetween(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * TICKET-ADV131 — earliest and latest trade date as {@code [min, max]},
     * used to build the year picker's options. Deliberately not a DISTINCT
     * EXTRACT(YEAR ...): a contiguous min..max range reads better in a
     * dropdown than a list with holes in it, and this stays portable SQL.
     * Both slots are null when the table is empty.
     *
     * Typed as a List even though the aggregate always yields exactly one row:
     * a bare {@code Object[]} return type is ambiguous to Spring Data, which
     * then cannot tell "one row of two columns" from "two single-column rows".
     */
    @Query("SELECT MIN(t.tradeDate), MAX(t.tradeDate) FROM Trade t")
    List<Object[]> findTradeDateBounds();
}
