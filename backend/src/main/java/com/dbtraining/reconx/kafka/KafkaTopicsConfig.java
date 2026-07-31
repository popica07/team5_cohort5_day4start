package com.dbtraining.reconx.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * ============================================================================
 * TICKET-ADV128 — Declare Kafka topics on app start (3 main topics + DLQ)
 * TICKET-ADV134 — DLQ topic must be pre-declared for DeadLetterPublishingRecoverer
 *
 * WHAT:    Creates 4 topics if they don't already exist (trade-events,
 *          recon-results, system-alerts, trade-events-dlq).
 * HOW:     NewTopic @Bean instances are picked up by KafkaAdmin which
 *          creates them at application startup.
 * WHY:     Auto-create in code keeps `docker compose up` -> "ready" in one
 *          step. No manual `kafka-topics --create` ceremony.
 * OBSERVE: Kafdrop (http://localhost:9000) lists all 4 topics after startup.
 * ============================================================================
 */
@Configuration
@Profile("!dev & !test")
public class KafkaTopicsConfig {

    public static final String TRADE_EVENTS = "trade-events";
    public static final String TRADE_EVENTS_DLQ = "trade-events-dlq";
    public static final String RECON_RESULTS = "recon-results";
    public static final String SYSTEM_ALERTS = "system-alerts";

    @Bean
    public NewTopic tradeEvents() {
        return TopicBuilder.name(TRADE_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic tradeEventsDlq() {
        return TopicBuilder.name(TRADE_EVENTS_DLQ)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reconResults() {
        return TopicBuilder.name(RECON_RESULTS)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic systemAlerts() {
        return TopicBuilder.name(SYSTEM_ALERTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}