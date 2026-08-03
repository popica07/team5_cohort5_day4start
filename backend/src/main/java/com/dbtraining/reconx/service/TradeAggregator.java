package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * TICKET-ADV137 — Event-sourcing rebuild
 *
 * WHAT:    Reconstructs a trade's current state purely from the append-only
 *          audit_log written by ADV132's AuditEventConsumer — the `trades`
 *          table is never consulted.
 * HOW:     Read every event for the tradeRef in chronological order and fold
 *          them into a running state: CREATED/UPDATED overwrite it with the
 *          event's after-snapshot, CANCELLED clears it.
 * WHY:     Proves the event log alone is sufficient to rebuild state. If
 *          `trades` is ever dropped or mis-migrated, replay reconstructs it.
 * OBSERVE: CREATED -> UPDATED -> CANCELLED rebuilds to Optional.empty();
 *          the same sequence without the CANCELLED yields the last UPDATED
 *          snapshot.
 * ============================================================================
 */
@Service
public class TradeAggregator {

    private static final Logger log = LoggerFactory.getLogger(TradeAggregator.class);

    private final AuditLogRepository auditRepo;
    private final ObjectMapper objectMapper;

    public TradeAggregator(AuditLogRepository auditRepo, ObjectMapper objectMapper) {
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Folds the trade's event stream into its current state.
     *
     * @return the rebuilt state, or empty when the trade has no events at all
     *         or its last meaningful event was a cancellation.
     */
    @Transactional(readOnly = true)
    public Optional<JsonNode> rebuild(String tradeRef) {
        // Ordered by event_timestamp, never by eventId: those are UUIDs and are
        // not monotonic, so ordering by them would replay events out of sequence.
        List<AuditLogEntry> events = auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef);
        if (events.isEmpty()) {
            return Optional.empty();
        }

        JsonNode state = null;
        for (AuditLogEntry event : events) {
            TradeEvent.EventType type = parseType(event);
            if (type == null) {
                continue; // unknown/corrupt row — skip rather than abort the rebuild
            }

            // Switching on the type (rather than blindly assigning afterState)
            // matters: TRADE_CANCELLED carries a null after-snapshot, and so can
            // a sparse UPDATE. Only CANCELLED should clear the accumulated state.
            switch (type) {
                case TRADE_CREATED, TRADE_UPDATED -> state = readState(event);
                case TRADE_CANCELLED -> state = null;
            }
        }

        return Optional.ofNullable(state);
    }

    private TradeEvent.EventType parseType(AuditLogEntry event) {
        try {
            return TradeEvent.EventType.valueOf(event.getEventType());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Skipping audit row {} for trade {}: unrecognised event type '{}'",
                    event.getEventId(), event.getTradeRef(), event.getEventType());
            return null;
        }
    }

    /**
     * after_state is stored as TEXT holding JSON, so it is parsed back into a
     * JsonNode here to satisfy the ticket's Optional&lt;JsonNode&gt; contract.
     * A blank column is a legitimate "no snapshot" and yields null.
     */
    private JsonNode readState(AuditLogEntry event) {
        String raw = event.getAfterState();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            log.warn("Audit row {} for trade {} has unparseable after_state; treating as no snapshot",
                    event.getEventId(), event.getTradeRef());
            return null;
        }
    }
}
