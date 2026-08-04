package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.model.DlqMessage;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================================
 * TICKET-ADV136 — DLQ Admin Controller (part 2 of 2)
 *
 * WHAT:    REST endpoints for inspecting and replaying quarantined DLQ messages.
 * HOW:     GET  /api/v1/admin/dlq          — list all unresolved DLQ rows
 *          POST /api/v1/admin/dlq/replay   — re-publish one event by eventId
 *          (dryRun=true returns what would be replayed without doing it)
 * WHY:     ADV134's DLQ is the quarantine; this endpoint is the escape hatch.
 *          One-at-a-time replay by eventId prevents the bulk-replay anti-pattern
 *          (replaying everything while the bug is still live just refills the DLQ).
 *          The ADMIN role gate echoes the RBAC contract from Day 5.
 * OBSERVE: POST /api/v1/admin/dlq/replay?eventId=<uuid>&dryRun=true
 *          returns the would-be payload without sending it.
 *          POST without dryRun re-publishes via TradeEventProducer and deletes
 *          the DLQ row; GET /api/v1/admin/dlq confirms it's gone.
 *
 * GOTCHA:  The replay endpoint MUST publish to `trade-events` (via TradeEventProducer),
 *          NOT directly to `trade-events-dlq`. Replaying to the DLQ would just
 *          re-quarantine the same message in an infinite loop.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
public class DlqAdminController {

    private final DlqMessageRepository repo;
    private final TradeEventProducer producer;
    private final ObjectMapper objectMapper;

    public DlqAdminController(DlqMessageRepository repo, TradeEventProducer producer,
                              ObjectMapper objectMapper) {
        this.repo = repo;
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<DlqMessage> listAll() {
        return repo.findAll();
    }

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replay(
            @RequestParam UUID eventId,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        DlqMessage msg = repo.findByEventId(eventId.toString())
                .orElseThrow(() -> new IllegalArgumentException("No DLQ message with eventId: " + eventId));

        if (dryRun) {
            return ResponseEntity.ok(Map.of(
                    "dryRun", true,
                    "wouldReplayTo", msg.getOriginalTopic(),
                    "tradeRef", msg.getTradeRef(),
                    "eventId", eventId
            ));
        }

        // The DLQ row stores the event as JSON; TradeEventProducer wants the record back.
        TradeEvent event;
        try {
            event = objectMapper.readValue(msg.getPayload(), TradeEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable DLQ payload for eventId: " + eventId, e);
        }

        producer.publish(event);
        repo.delete(msg);

        return ResponseEntity.ok(Map.of(
                "replayed", true,
                "eventId", eventId,
                "topic", msg.getOriginalTopic(),
                "tradeRef", msg.getTradeRef()
        ));
    }
}