package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================================================
 * TICKET-ADV138 — Admin Audit Endpoint
 *
 * WHAT:    Exposes the immutable audit event log for any trade so admins
 *          and recon analysts can inspect full trade history without SQL.
 * HOW:     GET /api/v1/audit/trades/{tradeRef}         — full history list
 *          GET /api/v1/audit/trades/{tradeRef}/events  — same (Kafka-event alias)
 *          Both return AuditLogEntry rows ordered by eventTimestamp ASC.
 * WHY:     Recon analysts need this to debug breaks raised by the recon engine.
 *          Admins need it for compliance evidence. Together with TradeAggregator
 *          (ADV137) this gives operators both the raw event log AND the rebuilt
 *          state for any trade in the system.
 * OBSERVE: curl -H "Authorization: Bearer <ADMIN_TOKEN>"
 *               /api/v1/audit/trades/EQU-20260603-0001/events
 *          returns ordered JSON array; same request without a token → 401;
 *          with a TRADER token → 403.
 *
 * GOTCHA:  @PreAuthorize at CLASS level propagates to both methods — you do
 *          not need to annotate each method individually. If you forget this
 *          and annotate at method level only, adding a new method to the
 *          controller silently opens a security hole.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1/audit/trades")
@PreAuthorize("hasAnyRole('ADMIN', 'RECON_ANALYST')")
@Tag(name = "audit", description = "Trade audit history endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditLogRepository auditRepo;

    public AuditController(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @GetMapping("/{tradeRef}")
    @Operation(summary = "Get full audit history for a trade (by tradeRef), oldest first")
    public List<AuditLogEntry> history(@PathVariable String tradeRef) {
        return auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef);
    }

    @GetMapping("/{tradeRef}/events")
    @Operation(summary = "Stream of all Kafka-sourced events for a trade, oldest first")
    public List<AuditLogEntry> events(@PathVariable String tradeRef) {
        return auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef);
    }
}