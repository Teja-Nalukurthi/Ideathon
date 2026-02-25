package com.nidhi.bank;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Proxies key bank endpoints so the Android app only needs ONE server URL
 * (nidhi-backend:8081). The backend forwards to nidhi-bank:8082 transparently.
 *
 * Routes proxied:
 *   POST /bank/device/register  → nidhi-bank
 *   GET  /bank/account          → nidhi-bank
 */
@Slf4j
@RestController
public class BankProxyController {

    private final WebClient bankWebClient;

    public BankProxyController(@Qualifier("bankWebClient") WebClient bankWebClient) {
        this.bankWebClient = bankWebClient;
    }

    /** POST /bank/device/register — proxied to bank server */
    @PostMapping("/bank/device/register")
    public ResponseEntity<Map> registerDevice(@RequestBody Map<String, Object> body) {
        log.info("Proxy: POST /bank/device/register for device={}", body.get("deviceId"));
        try {
            Map resp = bankWebClient.post()
                    .uri("/bank/device/register")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Bank proxy error: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "Bank server unavailable: " + e.getMessage()));
        }
    }

    /** GET /bank/account?phone=... — proxied to bank server */
    @GetMapping("/bank/account")
    public ResponseEntity<Map> getAccount(@RequestParam String phone) {
        log.info("Proxy: GET /bank/account?phone={}", phone);
        try {
            Map resp = bankWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/bank/account").queryParam("phone", phone).build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Bank proxy error: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "Bank server unavailable: " + e.getMessage()));
        }
    }

    /** POST /bank/auth/login — proxied to bank server */
    @PostMapping("/bank/auth/login")
    public ResponseEntity<Map> login(@RequestBody Map<String, Object> body) {
        try {
            Map resp = bankWebClient.post()
                    .uri("/bank/auth/login")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Bank login proxy error: {}", e.getMessage());
            return ResponseEntity.status(502).body(
                    Map.of("success", false, "error", "Bank server unavailable"));
        }
    }

    /** GET /bank/account/info?account=... — proxied to bank server */
    @GetMapping("/bank/account/info")
    public ResponseEntity<Map> getAccountInfo(@RequestParam String account) {
        try {
            Map resp = bankWebClient.get()
                    .uri(u -> u.path("/bank/account/info").queryParam("account", account).build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Bank account info proxy error: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "Bank server unavailable"));
        }
    }

    /** GET /bank/account/transactions?account=... — proxied to bank server */
    @GetMapping("/bank/account/transactions")
    public ResponseEntity<List> getAccountTransactions(@RequestParam String account) {
        try {
            List resp = bankWebClient.get()
                    .uri(u -> u.path("/bank/account/transactions").queryParam("account", account).build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();
            return ResponseEntity.ok(resp != null ? resp : List.of());
        } catch (Exception e) {
            log.error("Bank transactions proxy error: {}", e.getMessage());
            return ResponseEntity.status(502).body(List.of());
        }
    }
}
