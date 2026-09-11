package com.github.aleksandarmilkov.mkpay;

import com.github.aleksandarmilkov.mkpay.domain.OutboxEvent;
import com.github.aleksandarmilkov.mkpay.domain.OutboxStatus;
import com.github.aleksandarmilkov.mkpay.repository.OutboxEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxRelayIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Value("${mkpay.kafka.topic.payment-events:mkpay.payment-events}")
    private String topicName;

    private KafkaConsumer<String, String> testKafkaConsumer;

    @BeforeEach
    void setUpConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        testKafkaConsumer = new KafkaConsumer<>(props);
        testKafkaConsumer.subscribe(Collections.singletonList(topicName));
    }

    @AfterEach
    void tearDown() {
        if (testKafkaConsumer != null) {
            testKafkaConsumer.close();
        }
        outboxEventRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Should relay PENDING outbox event to Kafka and mark status as PROCESSED")
    void shouldRelayOutboxEventToKafka() {

        String aggregateId = "PAY-" + UUID.randomUUID();
        String jsonPayload = """
                {"paymentId":"%s","amount":1500.00,"currency":"MKD","status":"SUCCESS"}
                """.formatted(aggregateId);

        OutboxEvent pendingEvent = OutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId(aggregateId)
                .eventType("PAYMENT_COMPLETED")
                .payload(jsonPayload)
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();

        OutboxEvent savedEvent = outboxEventRepository.saveAndFlush(pendingEvent);
        UUID eventId = savedEvent.getId();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    OutboxEvent updated = outboxEventRepository.findById(eventId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
                    assertThat(updated.getProcessedAt()).isNotNull();
                });

        ConsumerRecords<String, String> records = testKafkaConsumer.poll(Duration.ofSeconds(5));

        assertThat(records.isEmpty()).isFalse();
        ConsumerRecord<String, String> record = records.iterator().next();

        assertThat(record.key()).isEqualTo(aggregateId);
        assertThat(record.value()).contains("1500.00").contains("MKD");
    }
}