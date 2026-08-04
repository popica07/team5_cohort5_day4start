package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.model.DlqMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqMessageRepository repo;
    private final ObjectMapper objectMapper;

    public DlqConsumer(DlqMessageRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    // Uses Boot's auto-configured kafkaListenerContainerFactory — the
    // spring.kafka.consumer block already deserialises values as TradeEvent.
    @KafkaListener(
            topics = "trade-events-dlq",
            groupId = "dlq-monitor"
    )
    public void onDlqMessage(ConsumerRecord<String, TradeEvent> record,
                             @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exMsg,
                             @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false)
                             String originalTopic) {
        TradeEvent event = record.value();
        log.error("DLQ: trade={} eventId={} reason={}",
                event.tradeRef(), event.eventId(), exMsg);

        String eventId = event.eventId().toString();

        // event_id is UNIQUE — a redelivery of an already-quarantined message
        // must not blow up the listener with a constraint violation.
        if (repo.existsByEventId(eventId)) {
            log.debug("DLQ row already exists for eventId={}, skipping", eventId);
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Could not serialise DLQ payload for eventId={}", eventId, e);
            payload = null;
        }

        repo.save(new DlqMessage(
                eventId,
                event.tradeRef(),
                // The DLT_ORIGINAL_TOPIC header is what the message came from
                // (trade-events); record.topic() is the DLQ itself.
                originalTopic != null ? originalTopic : record.topic(),
                record.partition(),
                record.offset(),
                payload,
                truncate(exMsg, 2000),   // reason column is VARCHAR(2000)
                Instant.now()
        ));
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
