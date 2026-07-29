package com.dbtraining.reconx.service;

import com.dbtraining.reconx.repository.entity.Trade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ============================================================================
 * TICKET-ADV104 (backend half) — TradeStreamService
 *
 * WHAT:    Backs the /v1/trades/stream SSE endpoint the dashboard's
 *          EventSource (static-dashboard/js/sse.js) subscribes to.
 * HOW:     Each open connection is an SseEmitter kept in a thread-safe list.
 *          TradeService calls broadcast() after every create/update/status
 *          change; each subscribed emitter gets the new trade pushed as a
 *          JSON "trade" event.
 * WHY:     Deliberately independent of TradeEventProducer/Kafka (still an
 *          unimplemented stub pending TICKET-ADV129) — the live dashboard
 *          feed doesn't need a durable event log, just an in-process fan-out.
 * ============================================================================
 */
@Service
public class TradeStreamService {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L; // 30 min

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));

        // Force the response headers onto the wire straight away. Without this
        // nothing is written until the first trade, so the browser's
        // EventSource stays in CONNECTING and never fires onopen — the badge
        // would sit on "Connecting…" forever. A comment frame flushes the
        // headers without reaching onmessage, so it can't render a phantom card.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException ex) {
            emitters.remove(emitter);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    /**
     * Snapshots the trade now, but only pushes it to subscribers once the
     * surrounding transaction commits. Sending inline would let a later
     * rollback leave every connected dashboard showing a state that was
     * never persisted.
     */
    public void broadcast(Trade trade) {
        TradeStreamEvent payload = toEvent(trade);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(payload);
                }
            });
        } else {
            send(payload);
        }
    }

    private void send(TradeStreamEvent payload) {
        for (SseEmitter emitter : emitters) {
            try {
                // Deliberately UNNAMED: EventSource.onmessage only fires for the
                // default "message" type. Naming this event would silently break
                // both js/sse.js (ADV104) and Day 8's useTradeStream hook.
                emitter.send(SseEmitter.event().data(payload));
            } catch (IOException | IllegalStateException ex) {
                // Dead connection — drop it, the client's EventSource will reconnect.
                emitters.remove(emitter);
            }
        }
    }

    private TradeStreamEvent toEvent(Trade trade) {
        String symbol = trade.getInstrument() != null ? trade.getInstrument().getSymbol() : null;
        return new TradeStreamEvent(
                trade.getTradeRef(), symbol, trade.getQuantity(), trade.getPrice(), trade.getStatus());
    }

    /** Field names match what static-dashboard/js/sse.js's prependTradeRow expects. */
    public record TradeStreamEvent(
            String tradeRef, String symbol, BigDecimal qty, BigDecimal price, String status) {
    }
}
