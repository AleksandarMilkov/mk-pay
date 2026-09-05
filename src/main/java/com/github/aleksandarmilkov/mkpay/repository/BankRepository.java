package com.github.aleksandarmilkov.mkpay.repository;

import com.github.aleksandarmilkov.mkpay.domain.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {
    Optional<Bank> findByCode(String code);
}