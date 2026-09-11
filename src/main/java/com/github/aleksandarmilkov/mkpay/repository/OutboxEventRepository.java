package com.github.aleksandarmilkov.mkpay.repository;

import com.github.aleksandarmilkov.mkpay.domain.OutboxEvent;
import com.github.aleksandarmilkov.mkpay.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;


public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = "SELECT pg_try_advisory_xact_lock(:lockId)", nativeQuery = true)
    boolean tryAdvisoryXactLock(@Param("lockId") long lockId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC")
    List<OutboxEvent> findTop50ByStatusForUpdateSkipLocked(@Param("status") OutboxStatus status, Pageable pageable);
}