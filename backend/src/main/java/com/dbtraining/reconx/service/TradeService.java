package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.MonthlyTradeStats;
import com.dbtraining.reconx.dto.TradeRequest;
import com.dbtraining.reconx.exception.DuplicateTradeRefException;
import com.dbtraining.reconx.exception.TradeNotFoundException;
import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.observability.TradeMetrics;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Trade;
import com.dbtraining.reconx.dto.TradeEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.dbtraining.reconx.repository.TradeSpecifications.*;

/**
 * ============================================================================
 * TICKET-ADV064 — TradeService.create (POST endpoint backing)
 * TICKET-ADV065 — update
 * TICKET-ADV066 — updateStatus (PATCH)
 * TICKET-ADV067 — softDelete
 * TICKET-ADV083 — increments trade_created_total Counter on create
 * TICKET-ADV129 — publishes TradeEvent on every state change
 * TICKET-ADV055/ADV056 — list() uses Specifications + filter query
 * ============================================================================
 */
@Service
@Transactional
public class TradeService {

    private final TradeRepository tradeRepo;
    private final CounterpartyRepository cpRepo;
    private final InstrumentRepository instRepo;
    private final TradeEventProducer events;
    private final TradeMetrics metrics;
    private final TradeStreamService stream;

    public TradeService(TradeRepository tradeRepo,
                        CounterpartyRepository cpRepo,
                        InstrumentRepository instRepo,
                        TradeEventProducer events,
                        TradeMetrics metrics,
                        TradeStreamService stream) {
        this.tradeRepo = tradeRepo;
        this.cpRepo = cpRepo;
        this.instRepo = instRepo;
        this.events = events;
        this.metrics = metrics;
        this.stream = stream;
    }

    public Trade create(TradeRequest req, String actor) {

    if (tradeRepo.findByTradeRef(req.tradeRef()).isPresent()) {
        throw new DuplicateTradeRefException(req.tradeRef());
    }

    var instrument = instRepo.findById(req.instrumentId())
            .orElseThrow(() ->
                    new TradeNotFoundException("instrumentId=" + req.instrumentId()));

    var counterparty = cpRepo.findById(req.counterpartyId())
            .orElseThrow(() ->
                    new TradeNotFoundException("counterpartyId=" + req.counterpartyId()));

    Trade trade = new Trade();

    trade.setTradeRef(req.tradeRef());
    trade.setInstrument(instrument);
    trade.setCounterparty(counterparty);
    trade.setAssetClass(req.assetClass());
    trade.setSide(req.side());
    trade.setQuantity(req.quantity());
    trade.setPrice(req.price());
    trade.setTradeDate(req.tradeDate());
    trade.setStatus("PENDING");

    Trade saved = tradeRepo.save(trade);

    metrics.incrementTradeCreated();
    metrics.recordTradeValue(
            req.quantity().multiply(req.price()).doubleValue()
    );

    // Leave this commented until ADV129 is implemented.
    // events.publish(...);

    stream.broadcast(saved);

    return saved;
}

    @Transactional
public Trade update(Long id, TradeRequest req, String actor) {

    Trade trade = tradeRepo.findById(id)
            .orElseThrow(() ->
                    new TradeNotFoundException("tradeId=" + id));

    tradeRepo.findByTradeRef(req.tradeRef())
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new DuplicateTradeRefException(req.tradeRef());
            });

    var instrument = instRepo.findById(req.instrumentId())
            .orElseThrow(() ->
                    new TradeNotFoundException(
                            "instrumentId=" + req.instrumentId()
                    ));

    var counterparty = cpRepo.findById(req.counterpartyId())
            .orElseThrow(() ->
                    new TradeNotFoundException(
                            "counterpartyId=" + req.counterpartyId()
                    ));

    trade.setTradeRef(req.tradeRef());
    trade.setInstrument(instrument);
    trade.setCounterparty(counterparty);
    trade.setAssetClass(req.assetClass());
    trade.setSide(req.side());
    trade.setQuantity(req.quantity());
    trade.setPrice(req.price());
    trade.setTradeDate(req.tradeDate());

    Trade saved = tradeRepo.save(trade);
    stream.broadcast(saved);
    return saved;
}

    public Trade updateStatus(Long id, String status, String actor) {
        // TODO(TICKET-ADV066): load, setStatus(status), save, publish TRADE_UPDATED
        //   with the new status in the "after" slot of the event.
        Trade t = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id=" + id));
        t.setStatus(status);
        String before = "status=" + t.getStatus();
        Trade saved = tradeRepo.save(t);
        stream.broadcast(saved);
        events.publish(new TradeEvent(UUID.randomUUID(), t.getTradeRef(),
                TradeEvent.EventType.TRADE_UPDATED, Instant.now(), actor, before, saved.getStatus()));
        return t;
    }

    public void softDelete(Long id, String actor) {
        Trade t = tradeRepo.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("id=" + id));
        t.softDelete();
        Trade saved = tradeRepo.save(t);
        stream.broadcast(saved);
        events.publish(new TradeEvent(UUID.randomUUID(), t.getTradeRef(),
                TradeEvent.EventType.TRADE_CANCELLED, Instant.now(), actor, null, null));
    }

    @Transactional(readOnly = true)
    public Page<Trade> list(LocalDate from, LocalDate to, String status, Long counterpartyId, Pageable pageable) {
        // Each factory returns cb.conjunction() for a null filter, so the three
        // compose unconditionally — no null-checks and no deprecated
        // Specification.where() (removed-for-deletion in Spring Data JPA 3.5).
        Specification<Trade> spec = tradeDateBetween(from, to)
                .and(hasStatus(status))
                .and(hasCounterparty(counterpartyId));
        return tradeRepo.findAll(spec, pageable);
    }

    /**
     * TICKET-ADV131 — trade counts for the twelve months of {@code year},
     * for the dashboard line chart.
     *
     * Always returns twelve points in calendar order: the SQL only reports
     * months that have trades, so the empty ones are padded here rather than
     * left to the chart to interpolate.
     */
    @Transactional(readOnly = true)
    public MonthlyTradeStats monthlyStats(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = from.plusYears(1).minusDays(1);

        long[] counts = new long[12];
        for (Object[] row : tradeRepo.countByMonthBetween(from, to)) {
            int month = ((Number) row[0]).intValue();
            if (month >= 1 && month <= 12) {
                counts[month - 1] = ((Number) row[1]).longValue();
            }
        }

        List<MonthlyTradeStats.MonthPoint> months = new ArrayList<>(12);
        long total = 0;
        for (int m = 1; m <= 12; m++) {
            months.add(new MonthlyTradeStats.MonthPoint(
                    m,
                    Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    counts[m - 1]));
            total += counts[m - 1];
        }

        return new MonthlyTradeStats(year, total, availableYears(year), months);
    }

    /**
     * Every year from the earliest trade to the latest, newest first. The
     * requested year and the current year are always offered even when
     * neither has any trades, so the picker can never render a selected
     * option that isn't in its own list.
     */
    private List<Integer> availableYears(int requestedYear) {
        var bounds = tradeRepo.findTradeDateBounds();
        LocalDate min = bounds.isEmpty() ? null : (LocalDate) bounds.getFirst()[0];
        LocalDate max = bounds.isEmpty() ? null : (LocalDate) bounds.getFirst()[1];

        int thisYear = LocalDate.now().getYear();
        int lo = Math.min(Math.min(requestedYear, thisYear), min == null ? thisYear : min.getYear());
        int hi = Math.max(Math.max(requestedYear, thisYear), max == null ? thisYear : max.getYear());

        List<Integer> years = new ArrayList<>(hi - lo + 1);
        for (int y = hi; y >= lo; y--) {
            years.add(y);
        }
        return years;
    }
}
