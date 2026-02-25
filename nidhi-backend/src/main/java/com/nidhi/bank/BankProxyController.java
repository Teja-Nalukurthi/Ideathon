package com.nidhi.bank;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

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
}
