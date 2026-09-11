package com.github.aleksandarmilkov.mkpay.service;

import com.github.aleksandarmilkov.mkpay.AbstractIntegrationTest;
import com.github.aleksandarmilkov.mkpay.domain.*;
import com.github.aleksandarmilkov.mkpay.dto.PaymentRequest;
import com.github.aleksandarmilkov.mkpay.dto.PaymentResponse;
import com.github.aleksandarmilkov.mkpay.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext // Ensures any state modified by multi-threaded writes doesn't taint other test contexts
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PaymentServiceConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BankAccountRepository accountRepository;

    @Autowired
    private PhoneAliasRepository phoneAliasRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private Long senderAccountId;
    private Long recipientAccountId;

    @Autowired
    private BankRepository bankRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        outboxEventRepository.deleteAll();
        phoneAliasRepository.deleteAll();
        accountRepository.deleteAll();
        bankRepository.deleteAll();

        Bank testBank = bankRepository.save(Bank.builder()
                .code("KMB")
                .name("Komercijalna Banka")
                .settlementAccount("1000000000000000")
                .active(true)
                .build());

        BankAccount senderAccount = accountRepository.save(BankAccount.builder()
                .accountNumber("2000000000000001")
                .ownerName("Sender User")
                .balance(new BigDecimal("1000.00"))
                .bank(testBank)
                .build());

        BankAccount recipientAccount = accountRepository.save(BankAccount.builder()
                .accountNumber("2000000000000002")
                .ownerName("Recipient User")
                .balance(new BigDecimal("500.00"))
                .bank(testBank)
                .build());

        senderAccountId = senderAccount.getId();
        recipientAccountId = recipientAccount.getId();

        phoneAliasRepository.save(PhoneAlias.builder()
                .phoneNumber("+38970111222")
                .bankAccount(senderAccount)
                .build());

        phoneAliasRepository.save(PhoneAlias.builder()
                .phoneNumber("+38970333444")
                .bankAccount(recipientAccount)
                .build());
    }

    @Test
    @DisplayName("Concurrent payments execute safely with pessimistic locking without overdrawing balance")
    void testConcurrentPaymentProcessing() throws InterruptedException {
        int numberOfThreads = 10;
        BigDecimal transferAmount = new BigDecimal("100.00"); // 10 transfers of 100 = 1000 total debited

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < numberOfThreads; i++) {
            final String idempotencyKey = UUID.randomUUID().toString();
            executorService.submit(() -> {
                try {
                    latch.await(); // Hold all threads until signal
                    PaymentRequest request = new PaymentRequest("+38970111222", "+38970333444", transferAmount);
                    PaymentResponse response = paymentService.processPayment(request, idempotencyKey);
                    if (response.state() == PaymentState.COMPLETED) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        latch.countDown(); // Release all threads at once to simulate flash concurrency
        boolean finished = completionLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(finished, "Execution timed out under concurrent load");

        // Assert exact balances
        BankAccount updatedSender = accountRepository.findById(senderAccountId).orElseThrow();
        BankAccount updatedRecipient = accountRepository.findById(recipientAccountId).orElseThrow();

        assertEquals(0, new BigDecimal("0.00").compareTo(updatedSender.getBalance()), "Sender balance should be exactly 0.00");
        assertEquals(0, new BigDecimal("1500.00").compareTo(updatedRecipient.getBalance()), "Recipient balance should be exactly 1500.00");
        assertEquals(10, successCount.get(), "All 10 payment operations should complete successfully");

        // Verify Outbox Atomicity: Exactly 10 outbox events created alongside payments
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertEquals(10, outboxEvents.size(), "Exactly 10 Outbox events should be persisted atomically");
    }

    @Test
    @DisplayName("Idempotency guarantee: Duplicate requests with same key return existing payment without double debit")
    void testIdempotentRequestsDoNotDoubleDebit() {
        String idempotencyKey = "IDEMPOTENT-KEY-12345";
        BigDecimal transferAmount = new BigDecimal("200.00");
        PaymentRequest request = new PaymentRequest("+38970111222", "+38970333444", transferAmount);

        PaymentResponse response1 = paymentService.processPayment(request, idempotencyKey);

        // Duplicate
        PaymentResponse response2 = paymentService.processPayment(request, idempotencyKey);

        assertEquals(response1.paymentId(), response2.paymentId(), "Payment IDs must match for duplicate idempotency key");

        BankAccount updatedSender = accountRepository.findById(senderAccountId).orElseThrow();
        assertEquals(0, new BigDecimal("800.00").compareTo(updatedSender.getBalance()), "Sender should only be debited once (800.00 remaining)");
    }
}