package com.nidhi.bank.transaction;

import com.nidhi.bank.user.BankUser;
import com.nidhi.bank.user.BankUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final BankUserRepository userRepo;
    private final BankTransactionRepository txRepo;

    /**
     * Execute a verified transfer. Called by the security backend after
     * challenge verification succeeds. This is the ONLY place money moves.
     */
    @Transactional
    public TransferResult executeTransfer(TransferRequest req) {
        // Resolve sender — try account number, phone, and device ID
        BankUser sender = userRepo.findByAccountNumber(req.fromAccount())
                .or(() -> userRepo.findByPhone(req.fromAccount()))
                .or(() -> userRepo.findByDeviceId(req.fromAccount()))
                .orElse(null);

        // Resolve recipient (account number or phone)
        BankUser recipient = userRepo.findByAccountNumber(req.toAccount())
                .or(() -> userRepo.findByPhone(req.toAccount()))
                .orElse(null);

        BankTransaction tx = new BankTransaction();
        tx.setReferenceId(req.referenceId());
        tx.setAmountPaise(req.amountPaise());
        tx.setEntropySourcesUsed(String.join(",", req.sourcesActive()));
        tx.setInsectCount(req.insectCount());
        tx.setResponseTimeMs(req.responseTimeMs());
        tx.setCreatedAt(Instant.now());

        if (sender == null) {
            tx.setFromAccount(req.fromAccount());
            tx.setFromName("Unknown");
            tx.setToAccount(req.toAccount());
            tx.setToName(recipient != null ? recipient.getFullName() : req.toAccount());
            tx.setStatus(BankTransaction.TxStatus.FAILED);
            tx.setBalanceAfterPaise(0);
            tx.setFailureReason("Sender account not found: " + req.fromAccount());
            txRepo.save(tx);
            return TransferResult.fail(tx, "Sender account not found");
        }

        tx.setFromAccount(sender.getAccountNumber());
        tx.setFromName(sender.getFullName());
        tx.setToAccount(recipient != null ? recipient.getAccountNumber() : req.toAccount());
        tx.setToName(recipient != null ? recipient.getFullName() : req.toAccount());

        // Check balance
        if (sender.getBalancePaise() < req.amountPaise()) {
            tx.setStatus(BankTransaction.TxStatus.FAILED);
            tx.setBalanceAfterPaise(sender.getBalancePaise());
            tx.setFailureReason("Insufficient balance");
            txRepo.save(tx);
            return TransferResult.fail(tx, "Insufficient balance");
        }

        // Debit sender
        long newSenderBalance = sender.getBalancePaise() - req.amountPaise();
        sender.setBalancePaise(newSenderBalance);
        userRepo.save(sender);

        // Credit recipient if they exist in our bank
        if (recipient != null) {
            recipient.setBalancePaise(recipient.getBalancePaise() + req.amountPaise());
            userRepo.save(recipient);
        }

        tx.setStatus(BankTransaction.TxStatus.SUCCESS);
        tx.setBalanceAfterPaise(newSenderBalance);
        txRepo.save(tx);

        log.info("Transfer: {} → {} | ₹{} | ref={}",
                sender.getFullName(), tx.getToName(),
                req.amountPaise() / 100, req.referenceId());

        return new TransferResult(true, tx, newSenderBalance, null);
    }

    public List<BankTransaction> getTransactionsForAccount(String accountNumber) {
        return txRepo.findByFromAccountOrToAccountOrderByCreatedAtDesc(accountNumber, accountNumber);
    }

    /** Get all transactions for a user by their database ID (for admin panel use) */
    public List<BankTransaction> getTransactionsByUserId(Long userId) {
        BankUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return txRepo.findByFromAccountOrToAccountOrderByCreatedAtDesc(
                user.getAccountNumber(), user.getAccountNumber());
    }

    public List<BankTransaction> getAllTransactions() {
        return txRepo.findAllByOrderByCreatedAtDesc();
    }

    /** Admin manually credits a user's account (no challenge needed — local admin action) */
    @Transactional
    public BankTransaction adminCredit(Long userId, long amountPaise, String note) {
        BankUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        long newBalance = user.getBalancePaise() + amountPaise;
        user.setBalancePaise(newBalance);
        userRepo.save(user);

        BankTransaction tx = new BankTransaction();
        tx.setReferenceId("ADM-CR-" + System.currentTimeMillis());
        tx.setFromAccount("ADMIN");
        tx.setFromName("Bank Admin");
        tx.setToAccount(user.getAccountNumber());
        tx.setToName(user.getFullName());
        tx.setAmountPaise(amountPaise);
        tx.setBalanceAfterPaise(newBalance);
        tx.setStatus(BankTransaction.TxStatus.SUCCESS);
        tx.setTxType(BankTransaction.TxType.ADMIN_CREDIT);
        tx.setAdminNote(note);
        tx.setEntropySourcesUsed("ADMIN_ACTION");
        txRepo.save(tx);

        log.info("Admin CREDIT: {} +₹{} | note={} | newBal={}",
                user.getFullName(), amountPaise / 100, note, newBalance / 100);
        return tx;
    }

    /** Admin manually debits a user's account */
    @Transactional
    public BankTransaction adminDebit(Long userId, long amountPaise, String note) {
        BankUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (user.getBalancePaise() < amountPaise) {
            throw new IllegalArgumentException(
                    "Insufficient balance. Current: ₹" + user.getBalancePaise() / 100);
        }

        long newBalance = user.getBalancePaise() - amountPaise;
        user.setBalancePaise(newBalance);
        userRepo.save(user);

        BankTransaction tx = new BankTransaction();
        tx.setReferenceId("ADM-DR-" + System.currentTimeMillis());
        tx.setFromAccount(user.getAccountNumber());
        tx.setFromName(user.getFullName());
        tx.setToAccount("ADMIN");
        tx.setToName("Bank Admin");
        tx.setAmountPaise(amountPaise);
        tx.setBalanceAfterPaise(newBalance);
        tx.setStatus(BankTransaction.TxStatus.SUCCESS);
        tx.setTxType(BankTransaction.TxType.ADMIN_DEBIT);
        tx.setAdminNote(note);
        tx.setEntropySourcesUsed("ADMIN_ACTION");
        txRepo.save(tx);

        log.info("Admin DEBIT: {} -₹{} | note={} | newBal={}",
                user.getFullName(), amountPaise / 100, note, newBalance / 100);
        return tx;
    }

    public DashboardStats getDashboardStats() {
        long txLast24h  = txRepo.countByCreatedAtAfter(Instant.now().minusSeconds(86400));
        Long vol        = txRepo.sumSuccessfulAmountAfter(Instant.now().minusSeconds(86400));
        long totalUsers  = userRepo.count();
        long activeUsers = userRepo.countByActive(true);
        return new DashboardStats(totalUsers, activeUsers, txLast24h, vol != null ? vol : 0L);
    }

    // ── DTOs ──────────────────────────────────────────────────────

    public record TransferRequest(
            String referenceId,
            String fromAccount,
            String toAccount,
            long amountPaise,
            String[] sourcesActive,
            int insectCount,
            long responseTimeMs
    ) {}

    public record TransferResult(boolean success, BankTransaction transaction,
                                 long balanceAfterPaise, String error) {
        static TransferResult fail(BankTransaction tx, String reason) {
            return new TransferResult(false, tx, 0, reason);
        }
    }

    public record DashboardStats(long totalUsers, long activeUsers, long txLast24h, long totalVolumePaise) {}
}
