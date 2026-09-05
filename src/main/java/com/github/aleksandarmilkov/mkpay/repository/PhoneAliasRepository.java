package com.github.aleksandarmilkov.mkpay.repository;

import com.github.aleksandarmilkov.mkpay.domain.PhoneAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PhoneAliasRepository extends JpaRepository<PhoneAlias, Long> {
    Optional<PhoneAlias> findByPhoneNumber(String phoneNumber);
}