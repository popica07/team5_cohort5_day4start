package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV129 — TradeEventProducer
 *
 * WHAT:    Publishes TradeEvent messages to the trade-events Kafka topic.
 *
 * HOW:     Uses tradeRef as the Kafka message key. Kafka therefore sends all
 *          events with the same tradeRef to the same partition, preserving
 *          their order.
 *
 * WHY:     Consumers must process events such as TRADE_CREATED before
 *          TRADE_UPDATED for the same trade.
 *
 * OBSERVE: Kafdrop -> trade-events -> Messages. The record key should equal
 *          the event's tradeRef.
 * ============================================================================
 */
@Component
public class TradeEventProducer {

    private static final Logger log =
            LoggerFactory.getLogger(TradeEventProducer.class);

    private static final String TOPIC = "trade-events";

    private final KafkaTemplate<String, TradeEvent> template;

    public TradeEventProducer(KafkaTemplate<String, TradeEvent> template) {
        this.template = template;
    }

    /**
     * Publishes a TradeEvent asynchronously to Kafka.
     *
     * @param event the TradeEvent to publish
     */
    public void publish(TradeEvent event) {
        log.debug(
                "Publishing TradeEvent eventId={} ref={} type={}",
                event.eventId(),
                event.tradeRef(),
                event.eventType()
        );

        template.send(TOPIC, event.tradeRef(), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error(
                                "Failed to publish TradeEvent eventId={} tradeRef={}",
                                event.eventId(),
                                event.tradeRef(),
                                exception
                        );
                    } else {
                        log.info(
                                "Published TradeEvent eventId={} tradeRef={} partition={} offset={}",
                                event.eventId(),
                                event.tradeRef(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()
                        );
                    }
                });
    }
}