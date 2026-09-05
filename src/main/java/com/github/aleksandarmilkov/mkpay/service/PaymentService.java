package com.github.aleksandarmilkov.mkpay.service;

import com.github.aleksandarmilkov.mkpay.domain.*;
import com.github.aleksandarmilkov.mkpay.dto.PaymentAuditEventDto;
import com.github.aleksandarmilkov.mkpay.dto.PaymentRequest;
import com.github.aleksandarmilkov.mkpay.dto.PaymentResponse;
import com.github.aleksandarmilkov.mkpay.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PhoneAliasRepository phoneAliasRepository;
    private final BankAccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAuditEventRepository auditEventRepository;

    private final Counter paymentSuccessCounter;
    private final Timer paymentTimer;

    public PaymentService(PhoneAliasRepository phoneAliasRepository,
                          BankAccountRepository accountRepository,
                          PaymentRepository paymentRepository,
                          PaymentAuditEventRepository auditEventRepository,
                          MeterRegistry meterRegistry) {
        this.phoneAliasRepository = phoneAliasRepository;
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.auditEventRepository = auditEventRepository;

        this.paymentSuccessCounter = Counter.builder("mkpay.payments.success.count")
                .description("Total number of successfully completed payments")
                .register(meterRegistry);

        this.paymentTimer = Timer.builder("mkpay.payments.execution.time")
                .description("Latency timer for end-to-end payment processing")
                .register(meterRegistry);
    }

    private void logAuditEvent(UUID paymentId, PaymentState state, String details) {
        auditEventRepository.save(PaymentAuditEvent.builder()
                .paymentId(paymentId)
                .state(state)
                .details(details)
                .build());
    }

    @Transactional
    @Retryable(
            retryFor = { ObjectOptimisticLockingFailureException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        return paymentTimer.record(() -> {

            Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existingPayment.isPresent()) {
                return mapToResponse(existingPayment.get());
            }

            PhoneAlias senderAlias = phoneAliasRepository.findByPhoneNumber(request.senderPhone())
                    .orElseThrow(() -> new IllegalArgumentException("Sender phone alias not registered: " + request.senderPhone()));

            PhoneAlias recipientAlias = phoneAliasRepository.findByPhoneNumber(request.recipientPhone())
                    .orElseThrow(() -> new IllegalArgumentException("Recipient phone alias not registered: " + request.recipientPhone()));

            Long senderAccountId = senderAlias.getBankAccount().getId();
            Long recipientAccountId = recipientAlias.getBankAccount().getId();

            BankAccount senderAccount;
            BankAccount recipientAccount;

            if (senderAccountId < recipientAccountId) {
                senderAccount = accountRepository.findByIdForUpdate(senderAccountId)
                        .orElseThrow(() -> new IllegalStateException("Sender account not found: " + senderAccountId));
                recipientAccount = accountRepository.findByIdForUpdate(recipientAccountId)
                        .orElseThrow(() -> new IllegalStateException("Recipient account not found: " + recipientAccountId));
            } else {
                recipientAccount = accountRepository.findByIdForUpdate(recipientAccountId)
                        .orElseThrow(() -> new IllegalStateException("Recipient account not found: " + recipientAccountId));
                senderAccount = accountRepository.findByIdForUpdate(senderAccountId)
                        .orElseThrow(() -> new IllegalStateException("Sender account not found: " + senderAccountId));
            }

            Payment payment = Payment.builder()
                    .idempotencyKey(idempotencyKey)
                    .senderPhone(request.senderPhone())
                    .recipientPhone(request.recipientPhone())
                    .amount(request.amount())
                    .currency("MKD")
                    .state(PaymentState.PENDING)
                    .build();

            payment = paymentRepository.saveAndFlush(payment);
            logAuditEvent(payment.getId(), PaymentState.PENDING, "Payment initiated");

            payment.setState(PaymentState.DEBIT_INITIATED);
            senderAccount.debit(request.amount());
            accountRepository.save(senderAccount);

            payment.setState(PaymentState.DEBIT_SUCCESS);
            logAuditEvent(payment.getId(), PaymentState.DEBIT_SUCCESS, "Debited sender phone: " + request.senderPhone());

            payment.setState(PaymentState.CREDIT_INITIATED);
            recipientAccount.credit(request.amount());
            accountRepository.save(recipientAccount);

            payment.setState(PaymentState.COMPLETED);
            payment = paymentRepository.saveAndFlush(payment);
            logAuditEvent(payment.getId(), PaymentState.COMPLETED, "Credited recipient phone: " + request.recipientPhone());

            paymentSuccessCounter.increment();
            return mapToResponse(payment);
        });
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getSenderPhone(),
                payment.getRecipientPhone(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getState(),
                payment.getFailureReason(),
                payment.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditEventDto> getAuditTrail(UUID paymentId) {
        return auditEventRepository.findByPaymentIdOrderByTimestampAsc(paymentId)
                .stream()
                .map(event -> new PaymentAuditEventDto(
                        event.getId(),
                        event.getPaymentId(),
                        event.getState(),
                        event.getDetails(),
                        event.getTimestamp()
                ))
                .toList();
    }

}