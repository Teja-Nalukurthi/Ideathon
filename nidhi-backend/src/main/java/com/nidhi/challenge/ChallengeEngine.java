package com.nidhi.challenge;

import com.nidhi.entropy.EntropyPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeEngine {

    private final EntropyPool entropyPool;

    @Value("${challenge.seed.ttl.ms:5000}") private long seedTtlMs;
    @Value("${challenge.response.ttl.ms:500}") private long responseTtlMs;
    @Value("${challenge.max.failures:3}") private int maxFailures;
    @Value("${challenge.bypass.signature:false}") private boolean bypassSignature;

    private final ConcurrentHashMap<String, PendingChallenge> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> deviceCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KeyPair> deviceKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final Set<String> locked = ConcurrentHashMap.newKeySet();

    public ChallengePacket issueChallenge(String deviceId, String txPayload, String clientNonceHex) {
        if (locked.contains(deviceId)) {
            throw new SecurityException("Device locked after repeated failures: " + deviceId);
        }

        deviceKeys.computeIfAbsent(deviceId, id -> generateKeyPair());

        long counter = deviceCounters.merge(deviceId, 1L, Long::sum);

        byte[] clientNonce = null;
        if (clientNonceHex != null && !clientNonceHex.isBlank()) {
            try { clientNonce = HexFormat.of().parseHex(clientNonceHex); }
            catch (Exception e) { log.warn("Invalid client nonce, ignoring"); }
        }

        EntropyPool.EntropyResult entropy = entropyPool.generateSeed(clientNonce);

        String txHash = sha256Hex(txPayload);
        String signingPayload = entropy.seedHex() + "|" + txHash + "|" + counter;

        String txId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        pending.put(txId, new PendingChallenge(txId, deviceId, entropy.seedHex(),
                signingPayload, txPayload, counter, entropy.sourcesActive(),
                entropy.insectCount(), now, now + seedTtlMs));

        KeyPair kp = deviceKeys.get(deviceId);
        // DEMO ONLY: private key returned to client for signing simulation
        String privateKeyB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String publicKeyB64  = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

        log.info("Challenge issued: txId={} device={} counter={} seed={}...",
                txId, deviceId, counter, entropy.seedHex().substring(0, 8));

        return new ChallengePacket(txId, entropy.seedHex(), signingPayload, counter,
                now + seedTtlMs, entropy.sourcesActive(), privateKeyB64, publicKeyB64);
    }

    public VerificationResult verify(String txId, String deviceId, String signatureB64) {
        PendingChallenge p = pending.get(txId);

        if (p == null) {
            recordFailure(deviceId);
            return VerificationResult.fail("CHALLENGE_EXPIRED",
                    "Challenge not found or expired. Transactions must complete within 5 seconds.");
        }
        if (!p.deviceId().equals(deviceId)) {
            recordFailure(deviceId);
            return VerificationResult.fail("DEVICE_MISMATCH", "Challenge issued to different device.");
        }

        long now = System.currentTimeMillis();
        if (now > p.expiresAt()) {
            pending.remove(txId);
            recordFailure(deviceId);
            return VerificationResult.fail("TTL_EXPIRED", "Challenge expired. Seed permanently erased.");
        }

        long responseMs = now - p.issuedAt();
        if (responseMs > responseTtlMs) {
            log.warn("Response time {}ms > inner TTL {}ms — logging only", responseMs, responseTtlMs);
        }

        boolean sigOk = bypassSignature || verifySignature(p.signingPayload(), signatureB64, deviceKeys.get(deviceId));
        if (!sigOk) {
            pending.remove(txId);
            recordFailure(deviceId);
            return VerificationResult.fail("INVALID_SIGNATURE", "Signature verification failed.");
        }
        if (bypassSignature) log.warn("[DEV] Signature bypass active — not for production!");

        pending.remove(txId);
        resetFailures(deviceId);

        log.info("Challenge verified: txId={} device={} counter={} responseTime={}ms",
                txId, deviceId, p.counter(), responseMs);

        return VerificationResult.success(txId, p.txPayload(), p.seedHex(),
                p.sourcesActive(), p.insectCount(), responseMs);
    }

    @Scheduled(fixedDelay = 500)
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (var entry : pending.entrySet()) {
            if (now > entry.getValue().expiresAt()) {
                pending.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) log.debug("Erased {} expired challenge seeds", removed);
    }

    private void recordFailure(String deviceId) {
        int count = failures.computeIfAbsent(deviceId, id -> new AtomicInteger(0))
                .incrementAndGet();
        if (count >= maxFailures) {
            locked.add(deviceId);
            log.warn("Device {} locked after {} failures", deviceId, count);
        }
    }

    private void resetFailures(String deviceId) {
        failures.getOrDefault(deviceId, new AtomicInteger()).set(0);
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("EC KeyPair generation failed", e);
        }
    }

    private boolean verifySignature(String payload, String signatureB64, KeyPair kp) {
        if (kp == null) return false;
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(kp.getPublic());
            sig.update(payload.getBytes());
            return sig.verify(Base64.getDecoder().decode(signatureB64));
        } catch (Exception e) {
            log.error("Signature error: {}", e.getMessage());
            return false;
        }
    }

    private String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) { return "hash_err"; }
    }

    public record PendingChallenge(String txId, String deviceId, String seedHex,
            String signingPayload, String txPayload, long counter, String[] sourcesActive,
            int insectCount, long issuedAt, long expiresAt) {}

    public record ChallengePacket(String txId, String seedHex, String signingPayload,
            long counter, long expiresAtMs, String[] sourcesActive,
            String privateKeyBase64,  // DEMO ONLY
            String publicKeyBase64) {}

    public record VerificationResult(boolean success, String txId, String txPayload,
            String errorCode, String errorMessage, String seedHex, String[] sourcesActive,
            int insectCount, long responseTimeMs) {

        static VerificationResult success(String txId, String txPayload, String seedHex,
                String[] sources, int insectCount, long responseMs) {
            return new VerificationResult(true, txId, txPayload, null, null,
                    seedHex, sources, insectCount, responseMs);
        }

        static VerificationResult fail(String code, String msg) {
            return new VerificationResult(false, null, null, code, msg,
                    null, new String[]{}, 0, 0);
        }
    }
}
