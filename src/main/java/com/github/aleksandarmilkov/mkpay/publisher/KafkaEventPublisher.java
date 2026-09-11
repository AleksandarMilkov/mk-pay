package com.github.aleksandarmilkov.mkpay.publisher;

import com.github.aleksandarmilkov.mkpay.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${mkpay.kafka.topic.payment-events:payment-events}")
    private String topic;

    @Override
    public CompletableFuture<Void> publish(OutboxEvent event) {
        return kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                .thenAccept(result -> log.info("Published outbox event [{}] to topic [{}] partition [{}] offset [{}]",
                        event.getId(), topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset()))
                .exceptionally(ex -> {
                    log.error("Kafka delivery failed for outbox event [{}]", event.getId(), ex);
                    throw new RuntimeException("Kafka publish execution failed", ex);
                });
    }
}