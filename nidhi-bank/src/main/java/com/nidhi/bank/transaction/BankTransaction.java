package com.nidhi.bank.transaction;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "bank_transactions")
@Data
@NoArgsConstructor
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NID-xxxxx reference from security backend
    @Column(nullable = false, unique = true)
    private String referenceId;

    @Column(nullable = false)
    private String fromAccount;

    @Column(nullable = false)
    private String toAccount;

    @Column(nullable = false)
    private String fromName;

    @Column(nullable = false)
    private String toName;

    @Column(nullable = false)
    private long amountPaise;

    @Column
    private long balanceAfterPaise;  // sender's balance after tx

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TxStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TxType txType = TxType.TRANSFER;

    @Column
    private String adminNote;   // reason for manual credit/debit

    @Column
    private String failureReason;

    // Entropy metadata from security backend
    @Column
    private String entropySourcesUsed;

    @Column
    private int insectCount;

    @Column
    private long responseTimeMs;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public enum TxStatus { SUCCESS, FAILED, REVERSED }
    public enum TxType  { TRANSFER, ADMIN_CREDIT, ADMIN_DEBIT }

    // Display helper
    @Transient
    public String getFormattedAmount() {
        long r = amountPaise / 100, p = amountPaise % 100;
        return p > 0 ? String.format("₹%,d.%02d", r, p) : String.format("₹%,d", r);
    }
}
