package com.github.aleksandarmilkov.mkpay.publisher;

import com.github.aleksandarmilkov.mkpay.domain.OutboxEvent;

import java.util.concurrent.CompletableFuture;

public interface EventPublisher {
    CompletableFuture<Void> publish(OutboxEvent event);
}