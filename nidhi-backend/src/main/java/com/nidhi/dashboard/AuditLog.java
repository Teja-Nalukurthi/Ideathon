package com.nidhi.dashboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class AuditLog {

    @Value("${audit.log.max.entries:100}") private int maxEntries;

    private final CopyOnWriteArrayList<AuditEntry> entries = new CopyOnWriteArrayList<>();

    private static final String GENESIS = "0".repeat(64);

    public void logChallengeIssued(String txId, String deviceId, String seedPreview,
                                    String[] sources, int insectCount) {
        append(EventType.CHALLENGE_ISSUED, txId, deviceId,
                String.format("Seed: %s... | Sources: %s | Insects: %d",
                        seedPreview.substring(0, Math.min(8, seedPreview.length())),
                        String.join("+", sources), insectCount));
    }

    public void logChallengeVerified(String txId, String deviceId, long responseMs,
                                      String recipient, String amount) {
        append(EventType.CHALLENGE_VERIFIED, txId, deviceId,
                String.format("Verified in %dms | %s -> %s", responseMs, amount, recipient));
    }

    public void logChallengeFailed(String txId, String deviceId, String reason) {
        append(EventType.CHALLENGE_FAILED, txId, deviceId, "Failed: " + reason);
    }

    public void logTransactionCommitted(String txId, String ref, String recipient, String amount) {
        append(EventType.TRANSACTION_COMMITTED, txId, "LEDGER",
                String.format("REF:%s | %s -> %s | COMMITTED", ref, amount, recipient));
    }

    public List<AuditEntry> getRecentEntries(int n) {
        var all = new ArrayList<>(entries);
        Collections.reverse(all);
        return all.subList(0, Math.min(n, all.size()));
    }

    public List<AuditEntry> getAllEntries() {
        return Collections.unmodifiableList(entries);
    }

    private synchronized void append(EventType type, String txId, String actor, String details) {
        String prevHash = entries.isEmpty() ? GENESIS
                : entries.get(entries.size() - 1).entryHash();

        long timestamp = System.currentTimeMillis();
        int seq = entries.size() + 1;

        String hashInput = seq + "|" + type + "|" + txId + "|" + actor
                + "|" + details + "|" + timestamp + "|" + prevHash;

        String entryHash = sha256(hashInput);

        entries.add(new AuditEntry(seq, type, txId, actor, details,
                type != EventType.CHALLENGE_FAILED,
                Instant.ofEpochMilli(timestamp).toString(),
                prevHash, entryHash));

        while (entries.size() > maxEntries) entries.remove(0);

        log.info("AUDIT [{}] {} txId={}", seq, type, txId);
    }

    private String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) { return "err_" + System.nanoTime(); }
    }

    public enum EventType {
        CHALLENGE_ISSUED, CHALLENGE_VERIFIED, CHALLENGE_FAILED, TRANSACTION_COMMITTED
    }

    public record AuditEntry(int sequence, EventType type, String txId, String actor,
                              String details, boolean success, String timestamp,
                              String previousHash, String entryHash) {}
}
