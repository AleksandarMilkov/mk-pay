package com.github.aleksandarmilkov.mkpay.dto;

import com.github.aleksandarmilkov.mkpay.domain.PaymentState;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentAuditEventDto(
        UUID id,
        UUID paymentId,
        PaymentState state,
        String details,
        LocalDateTime timestamp
) {}