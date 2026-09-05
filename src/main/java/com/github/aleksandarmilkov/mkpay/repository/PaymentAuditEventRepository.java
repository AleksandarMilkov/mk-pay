package com.github.aleksandarmilkov.mkpay.repository;

import com.github.aleksandarmilkov.mkpay.domain.PaymentAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAuditEventRepository extends JpaRepository<PaymentAuditEvent, UUID> {
    List<PaymentAuditEvent> findByPaymentIdOrderByTimestampAsc(UUID paymentId);
}