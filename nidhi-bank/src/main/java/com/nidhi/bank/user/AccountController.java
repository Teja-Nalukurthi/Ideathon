package com.nidhi.bank.user;

import com.nidhi.bank.transaction.BankTransaction;
import com.nidhi.bank.transaction.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User-facing account endpoints — called from Android via nidhi-backend proxy.
 * No admin secret required; account number acts as the bearer token (LAN-only).
 */
@RestController
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final TransferService transferService;

    /** GET /bank/account/info?account=NIDHI... */
    @GetMapping("/bank/account/info")
    public ResponseEntity<?> getAccountInfo(@RequestParam String account) {
        return userService.getByAccountNumber(account)
                .map(u -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("accountNumber", u.getAccountNumber());
                    body.put("fullName",      u.getFullName());
                    body.put("phone",         u.getPhone());
                    body.put("balancePaise",  u.getBalancePaise());
                    body.put("languageCode",  u.getLanguageCode() != null ? u.getLanguageCode() : "hi");
                    body.put("active",        u.isActive());
                    body.put("deviceId",      u.getDeviceId());      // may be null → JSON null is fine
                    body.put("fcmToken",      u.getFcmToken());       // may be null
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /bank/account/transactions?account=NIDHI... — last 30 */
    @GetMapping("/bank/account/transactions")
    public ResponseEntity<List<BankTransaction>> getTransactions(@RequestParam String account) {
        List<BankTransaction> txs = transferService.getTransactionsForAccount(account);
        return ResponseEntity.ok(txs.stream().limit(30).toList());
    }
}
