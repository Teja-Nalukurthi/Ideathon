package com.nidhi.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/transaction")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService txService;

    /**
     * POST /transaction/initiate
     *
     * Request body:
     * {
     *   "audioBase64": "UklGRi...",       // nullable
     *   "textFallback": "send 500 to Ramu", // nullable
     *   "languageCode": "hi",
     *   "deviceId": "device-001",
     *   "clientNonceHex": null
     * }
     */
    @PostMapping("/initiate")
    public ResponseEntity<TransactionService.InitiateResponse> initiate(
            @RequestBody TransactionService.InitiateRequest req) {
        log.info("Initiate: device={} lang={}", req.deviceId(), req.languageCode());
        return ResponseEntity.ok(txService.initiate(req));
    }

    /**
     * POST /transaction/confirm
     *
     * Request body:
     * {
     *   "txId": "uuid-from-initiate",
     *   "deviceId": "device-001",
     *   "signatureBase64": "MEYCIQDx..."
     * }
     */
    @PostMapping("/confirm")
    public ResponseEntity<TransactionService.ConfirmResponse> confirm(
            @RequestBody ConfirmRequest req) {
        log.info("Confirm: txId={} device={}", req.txId(), req.deviceId());
        return ResponseEntity.ok(txService.confirm(req.txId(), req.deviceId(), req.signatureBase64()));
    }

    public record ConfirmRequest(String txId, String deviceId, String signatureBase64) {}
}
