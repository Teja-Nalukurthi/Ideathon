package com.nidhi.bank.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Value("${bank.internal.secret}")
    private String internalSecret;

    /**
     * POST /bank/internal/transfer
     * Called by the nidhi-backend (security backend) after a challenge is verified.
     * Header: X-Internal-Secret must match bank.internal.secret
     */
    @PostMapping("/bank/internal/transfer")
    public ResponseEntity<?> executeTransfer(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody TransferService.TransferRequest req) {

        if (!internalSecret.equals(secret)) {
            log.warn("Unauthorized transfer attempt with secret: {}", secret);
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        TransferService.TransferResult result = transferService.executeTransfer(req);

        if (result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "referenceId", result.transaction().getReferenceId(),
                    "balanceAfterPaise", result.balanceAfterPaise(),
                    "balanceFormatted", formatBalance(result.balanceAfterPaise())
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", result.error(),
                    "referenceId", result.transaction().getReferenceId()
            ));
        }
    }

    /** GET /admin/transactions — all transactions for admin dashboard */
    @GetMapping("/admin/transactions")
    public ResponseEntity<List<BankTransaction>> allTransactions() {
        return ResponseEntity.ok(transferService.getAllTransactions());
    }

    /** GET /admin/transactions/{account} — tx history for a specific account */
    @GetMapping("/admin/transactions/{account}")
    public ResponseEntity<List<BankTransaction>> accountTransactions(
            @PathVariable String account) {
        return ResponseEntity.ok(transferService.getTransactionsForAccount(account));
    }

    /** GET /admin/txstats — transaction volume stats */
    @GetMapping("/admin/txstats")
    public ResponseEntity<TransferService.DashboardStats> txStats() {
        return ResponseEntity.ok(transferService.getDashboardStats());
    }

    private String formatBalance(long paise) {
        long r = paise / 100, p = paise % 100;
        return p > 0 ? String.format("₹%,d.%02d", r, p) : String.format("₹%,d", r);
    }
}
