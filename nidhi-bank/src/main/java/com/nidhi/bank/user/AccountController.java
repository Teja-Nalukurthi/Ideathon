package com.nidhi.bank.user;

import com.nidhi.bank.transaction.BankTransaction;
import com.nidhi.bank.transaction.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * POST /bank/account/lookup-batch
     * Body: ["9876543210", "9123456789", ...]
     * Returns only those phone numbers that have a Nidhi account.
     * Used by the Android app to surface "Pay Contacts" from the user's phonebook.
     */
    @PostMapping("/bank/account/lookup-batch")
    public ResponseEntity<List<Map<String, String>>> lookupBatch(@RequestBody List<String> phones) {
        List<Map<String, String>> result = phones.stream()
                .map(this::normalizePhone)
                .map(userService::getByPhone)
                .filter(Optional::isPresent)
                .map(opt -> {
                    BankUser u = opt.get();
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("accountNumber", u.getAccountNumber());
                    m.put("fullName", u.getFullName());
                    m.put("phone", u.getPhone());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    /** Normalize a phone number to 10-digit Indian format */
    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0"))  digits = digits.substring(1);
        return digits;
    }
}
