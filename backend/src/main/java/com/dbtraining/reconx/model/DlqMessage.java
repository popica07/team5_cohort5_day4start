package com.dbtraining.reconx.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * TICKET-ADV136 — One quarantined Kafka message that exhausted its retry budget.
 * Written by DlqConsumer, read and replayed by DlqAdminController.
 */
@Entity
@Table(name = "dlq_messages")
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // String rather than UUID: the column is VARCHAR(36) (see 009-dlq.xml), and
    // Hibernate would map a UUID field to Postgres `uuid`, failing ddl-auto=validate.
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "trade_ref", length = 30)
    private String tradeRef;

    @Column(name = "original_topic", nullable = false, length = 100)
    private String originalTopic;

    @Column(name = "partition_no", nullable = false)
    private Integer partitionNo;

    @Column(name = "record_offset", nullable = false)
    private Long recordOffset;

    @Column(length = 1000000)
    private String payload;

    @Column(length = 2000)
    private String reason;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    protected DlqMessage() {}

    public DlqMessage(String eventId, String tradeRef, String originalTopic,
                      Integer partitionNo, Long recordOffset,
                      String payload, String reason, Instant firstSeen) {
        this.eventId = eventId;
        this.tradeRef = tradeRef;
        this.originalTopic = originalTopic;
        this.partitionNo = partitionNo;
        this.recordOffset = recordOffset;
        this.payload = payload;
        this.reason = reason;
        this.firstSeen = firstSeen;
    }

    public Long getId()              { return id; }
    public String getEventId()       { return eventId; }
    public String getTradeRef()      { return tradeRef; }
    public String getOriginalTopic() { return originalTopic; }
    public Integer getPartitionNo()  { return partitionNo; }
    public Long getRecordOffset()    { return recordOffset; }
    public String getPayload()       { return payload; }
    public String getReason()        { return reason; }
    public Instant getFirstSeen()    { return firstSeen; }
}
