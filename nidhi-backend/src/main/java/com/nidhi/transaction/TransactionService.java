package com.nidhi.transaction;

import com.nidhi.challenge.ChallengeEngine;
import com.nidhi.dashboard.AuditLog;
import com.nidhi.voice.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final VoiceService voiceService;
    private final ChallengeEngine challengeEngine;
    private final AuditLog auditLog;

    public InitiateResponse initiate(InitiateRequest req) {
        VoiceService.VoiceParseResult voice = voiceService.processInput(
                req.audioBase64(), req.textFallback(), req.languageCode());

        if (!voice.success()) {
            return InitiateResponse.error(voice.errorMessage());
        }

        String txPayload = String.format("TRANSFER|%s|%d|%s",
                voice.recipient(), voice.amountPaise(), req.deviceId());

        ChallengeEngine.ChallengePacket challenge = challengeEngine.issueChallenge(
                req.deviceId(), txPayload, req.clientNonceHex());

        auditLog.logChallengeIssued(challenge.txId(), req.deviceId(),
                challenge.seedHex(), challenge.sourcesActive(), 0);

        log.info("Initiated: txId={} recipient='{}' amount={}",
                challenge.txId(), voice.recipient(), voice.formattedAmount());

        return new InitiateResponse(true, challenge.txId(), voice.recipient(),
                voice.amountPaise(), voice.formattedAmount(), voice.confirmationText(),
                voice.language(), challenge.signingPayload(), challenge.expiresAtMs(),
                challenge.privateKeyBase64(), challenge.sourcesActive(), null);
    }

    public ConfirmResponse confirm(String txId, String deviceId, String signatureB64) {
        ChallengeEngine.VerificationResult result =
                challengeEngine.verify(txId, deviceId, signatureB64);

        if (!result.success()) {
            auditLog.logChallengeFailed(txId, deviceId, result.errorCode());
            return ConfirmResponse.error(result.errorCode(), result.errorMessage());
        }

        String[] parts = result.txPayload().split("\\|");
        String recipient = parts.length > 1 ? parts[1] : "Unknown";
        long amountPaise = parts.length > 2 ? safeParseLong(parts[2]) : 0;
        String formatted  = "Rs " + (amountPaise / 100);

        auditLog.logChallengeVerified(txId, deviceId, result.responseTimeMs(), recipient, formatted);

        // Mock ledger commit
        String ref = "NID-" + (System.currentTimeMillis() % 100000);
        auditLog.logTransactionCommitted(txId, ref, recipient, formatted);

        log.info("Committed: txId={} ref={} {}  -> {}", txId, ref, formatted, recipient);

        return new ConfirmResponse(true, txId, ref, recipient, amountPaise, formatted,
                result.seedHex(), result.sourcesActive(), result.responseTimeMs(), null, null);
    }

    private long safeParseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    public record InitiateRequest(
            String audioBase64,
            String textFallback,
            String languageCode,
            String deviceId,
            String clientNonceHex
    ) {}

    public record InitiateResponse(
            boolean success,
            String txId,
            String recipient,
            long amountPaise,
            String formattedAmount,
            String confirmationText,
            String language,
            String signingPayload,
            long expiresAtMs,
            String privateKeyBase64,  // DEMO ONLY
            String[] sourcesActive,
            String errorMessage) {

        static InitiateResponse error(String msg) {
            return new InitiateResponse(false, null, null, 0, null, null, null,
                    null, 0, null, new String[]{}, msg);
        }
    }

    public record ConfirmResponse(
            boolean success,
            String txId,
            String referenceNumber,
            String recipient,
            long amountPaise,
            String formattedAmount,
            String seedHex,
            String[] sourcesActive,
            long responseTimeMs,
            String errorCode,
            String errorMessage) {

        static ConfirmResponse error(String code, String msg) {
            return new ConfirmResponse(false, null, null, null, 0, null,
                    null, new String[]{}, 0, code, msg);
        }
    }
}
