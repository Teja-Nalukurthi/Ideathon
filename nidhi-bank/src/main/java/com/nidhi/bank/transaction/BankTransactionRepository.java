package com.nidhi.bank.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    List<BankTransaction> findByFromAccountOrToAccountOrderByCreatedAtDesc(
            String from, String to);

    List<BankTransaction> findAllByOrderByCreatedAtDesc();

    long countByCreatedAtAfter(Instant since);

    @Query("SELECT SUM(t.amountPaise) FROM BankTransaction t WHERE t.status = 'SUCCESS' AND t.createdAt > ?1")
    Long sumSuccessfulAmountAfter(Instant since);
}
