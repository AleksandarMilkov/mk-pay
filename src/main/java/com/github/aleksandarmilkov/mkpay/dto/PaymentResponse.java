package com.github.aleksandarmilkov.mkpay.dto;

import com.github.aleksandarmilkov.mkpay.domain.PaymentState;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String senderPhone,
        String recipientPhone,
        BigDecimal amount,
        String currency,
        PaymentState state,
        String failureReason,
        LocalDateTime createdAt
) {}