package com.github.aleksandarmilkov.mkpay.service;

import com.github.aleksandarmilkov.mkpay.domain.OutboxEvent;
import com.github.aleksandarmilkov.mkpay.domain.OutboxStatus;
import com.github.aleksandarmilkov.mkpay.repository.OutboxEventRepository;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@EnableScheduling
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxRelayScheduler(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusForUpdateSkipLocked(OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            event.setStatus(OutboxStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
        }
    }
}