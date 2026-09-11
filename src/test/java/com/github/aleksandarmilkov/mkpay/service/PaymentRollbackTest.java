package com.github.aleksandarmilkov.mkpay.service;

import com.github.aleksandarmilkov.mkpay.AbstractIntegrationTest;
import com.github.aleksandarmilkov.mkpay.domain.*;
import com.github.aleksandarmilkov.mkpay.dto.PaymentRequest;
import com.github.aleksandarmilkov.mkpay.exception.InsufficientFundsException;
import com.github.aleksandarmilkov.mkpay.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentRollbackTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BankAccountRepository accountRepository;

    @Autowired
    private PhoneAliasRepository phoneAliasRepository;

    @Autowired
    private BankRepository bankRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private EntityManager entityManager;

    private String senderPhone = "+38970111222";
    private String recipientPhone = "+38970333444";

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        phoneAliasRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        bankRepository.deleteAllInBatch();
        phoneAliasRepository.flush();
        accountRepository.flush();
        bankRepository.flush();

        Bank bank = new Bank();
        bank.setName("NLB Banka");
        bank.setCode("NLB");
        bank.setSettlementAccount("MK0720000000000000");
        bank.setActive(true);
        bank = bankRepository.save(bank);

        BankAccount senderAcc = new BankAccount();
        senderAcc.setAccountNumber("MK0720000000000001");
        senderAcc.setBalance(new BigDecimal("50.00"));
        senderAcc.setBank(bank);
        senderAcc.setOwnerName("Sender");
        senderAcc = accountRepository.save(senderAcc);

        BankAccount recipientAcc = new BankAccount();
        recipientAcc.setAccountNumber("MK0720000000000002");
        recipientAcc.setBalance(new BigDecimal("100.00"));
        recipientAcc.setBank(bank);
        recipientAcc.setOwnerName("Recipient");
        recipientAcc = accountRepository.save(recipientAcc);

        PhoneAlias senderAlias = new PhoneAlias();
        senderAlias.setPhoneNumber(senderPhone);
        senderAlias.setBankAccount(senderAcc);
        phoneAliasRepository.save(senderAlias);

        PhoneAlias recipientAlias = new PhoneAlias();
        recipientAlias.setPhoneNumber(recipientPhone);
        recipientAlias.setBankAccount(recipientAcc);
        phoneAliasRepository.save(recipientAlias);
    }
    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        phoneAliasRepository.deleteAll();
        accountRepository.deleteAll();
        bankRepository.deleteAll();
    }

    @Test
    void testInsufficientFunds_ShouldRollbackPaymentAndOutbox() {
        PaymentRequest request = new PaymentRequest(senderPhone, recipientPhone, new BigDecimal("500.00"));
        String idempotencyKey = UUID.randomUUID().toString();

        assertThrows(InsufficientFundsException.class, () -> {
            paymentService.processPayment(request, idempotencyKey);
        });

        entityManager.clear();

        BankAccount sender = accountRepository.findByAccountNumber("MK0720000000000001").orElseThrow();
        assertEquals(new BigDecimal("50.00"), sender.getBalance());

        assertTrue(paymentRepository.findByIdempotencyKey(idempotencyKey).isEmpty());

        assertEquals(0, outboxEventRepository.count());
    }

}