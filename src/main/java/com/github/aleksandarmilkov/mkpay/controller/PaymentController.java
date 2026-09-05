package com.github.aleksandarmilkov.mkpay.controller;

import com.github.aleksandarmilkov.mkpay.domain.PaymentAuditEvent;
import com.github.aleksandarmilkov.mkpay.dto.PaymentAuditEventDto;
import com.github.aleksandarmilkov.mkpay.dto.PaymentRequest;
import com.github.aleksandarmilkov.mkpay.dto.PaymentResponse;
import com.github.aleksandarmilkov.mkpay.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response = paymentService.processPayment(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}/audit")
    public ResponseEntity<List<PaymentAuditEventDto>> getAuditTrail(@PathVariable UUID paymentId) {
        List<PaymentAuditEventDto> auditTrail = paymentService.getAuditTrail(paymentId);
        return ResponseEntity.ok(auditTrail);
    }
}