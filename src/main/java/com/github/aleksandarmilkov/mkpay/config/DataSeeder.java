package com.github.aleksandarmilkov.mkpay.config;

import com.github.aleksandarmilkov.mkpay.domain.Bank;
import com.github.aleksandarmilkov.mkpay.domain.BankAccount;
import com.github.aleksandarmilkov.mkpay.domain.PhoneAlias;
import com.github.aleksandarmilkov.mkpay.repository.BankAccountRepository;
import com.github.aleksandarmilkov.mkpay.repository.BankRepository;
import com.github.aleksandarmilkov.mkpay.repository.PhoneAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final BankRepository bankRepository;
    private final BankAccountRepository accountRepository;
    private final PhoneAliasRepository phoneAliasRepository;

    @Override
    public void run(String... args) {
        if (bankRepository.count() > 0) return;

        Bank nlb = bankRepository.save(Bank.builder()
                .code("NLB")
                .name("NLB Banka")
                .settlementAccount("2100000000000001")
                .active(true)
                .build());

        Bank stb = bankRepository.save(Bank.builder()
                .code("STB")
                .name("Stopanska Banka")
                .settlementAccount("2000000000000001")
                .active(true)
                .build());

        BankAccount senderAcc = accountRepository.save(BankAccount.builder()
                .accountNumber("2101234567890123")
                .ownerName("Marko Angelov")
                .balance(new BigDecimal("50000.00"))
                .bank(nlb)
                .build());

        BankAccount recipientAcc = accountRepository.save(BankAccount.builder()
                .accountNumber("2009876543210987")
                .ownerName("Marija Petreska")
                .balance(new BigDecimal("12000.00"))
                .bank(stb)
                .build());

        phoneAliasRepository.save(PhoneAlias.builder()
                .phoneNumber("070111111")
                .bankAccount(senderAcc)
                .build());

        phoneAliasRepository.save(PhoneAlias.builder()
                .phoneNumber("070222222")
                .bankAccount(recipientAcc)
                .build());
    }
}