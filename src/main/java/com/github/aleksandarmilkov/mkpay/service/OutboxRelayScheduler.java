package com.github.aleksandarmilkov.mkpay.service;

import com.github.aleksandarmilkov.mkpay.domain.OutboxEvent;
import com.github.aleksandarmilkov.mkpay.domain.OutboxStatus;
import com.github.aleksandarmilkov.mkpay.publisher.EventPublisher;
import com.github.aleksandarmilkov.mkpay.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class OutboxRelayScheduler {

    private static final long OUTBOX_LOCK_ID = 8839201L;
    private static final int MAX_RETRIES = 5;
    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;

    public OutboxRelayScheduler(OutboxEventRepository outboxEventRepository,
                                EventPublisher eventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${mkpay.outbox.poller-delay-ms:5000}")
    @Transactional
    public void processOutboxEvents() {
        boolean lockAcquired = outboxEventRepository.tryAdvisoryXactLock(OUTBOX_LOCK_ID);
        if (!lockAcquired) {
            return;
        }

        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findTop50ByStatusForUpdateSkipLocked(OutboxStatus.PENDING, PageRequest.of(0, 50));

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            try {
                eventPublisher.publish(event).get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                event.setStatus(OutboxStatus.PROCESSED);
                event.setProcessedAt(Instant.now());
            } catch (TimeoutException te) {
                handleFailure(event, te, "Timed out waiting for Kafka ack");
            } catch (Exception ex) {
                handleFailure(event, ex.getCause() != null ? ex.getCause() : ex, "Failed to publish");
            }
        }
    }

    private void handleFailure(OutboxEvent event, Throwable cause, String reasonPrefix) {
        int nextRetry = event.getRetryCount() + 1;
        event.setRetryCount(nextRetry);

        if (nextRetry >= MAX_RETRIES) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event [{}] exceeded max retries. Marked as FAILED.", event.getId(), cause);
        } else {
            log.warn("{} for outbox event [{}]. Attempt {}/{}", reasonPrefix, event.getId(), nextRetry, MAX_RETRIES, cause);
        }
    }
}