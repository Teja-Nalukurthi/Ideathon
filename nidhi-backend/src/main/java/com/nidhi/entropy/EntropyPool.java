package com.nidhi.entropy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class EntropyPool {

    private final SecureRandom secureRandom = new SecureRandom();
    private final WebClient sidecarClient;

    @Value("${entropy.insect.min.count:2}")
    private int minInsectCount;

    private final AtomicReference<InsectReading> lastInsectReading = new AtomicReference<>();
    private final AtomicInteger insectCount = new AtomicInteger(0);
    private final AtomicLong lastInsectUpdateMs = new AtomicLong(0);
    private final AtomicReference<String[]> lastActiveSources =
            new AtomicReference<>(new String[]{});

    public EntropyPool(@Qualifier("sidecarWebClient") WebClient sidecarClient) {
        this.sidecarClient = sidecarClient;
    }

    public EntropyResult generateSeed(byte[] clientNonce) {
        try {
            long t0 = System.nanoTime();

            // S1: Hardware TRNG
            byte[] s1 = new byte[32];
            secureRandom.nextBytes(s1);

            // S2: Insect coordinates
            InsectReading reading = fetchInsectReading();
            byte[] s2 = hashInsectReading(reading);
            boolean insectActive = reading != null && reading.count() >= minInsectCount;

            // S3: Timing noise (4 samples with busy-wait)
            byte[] s3 = new byte[32];
            for (int i = 0; i < 4; i++) {
                byte[] nanoBytes = longToBytes(System.nanoTime());
                for (int j = 0; j < 8; j++) s3[i * 8 + j] ^= nanoBytes[j];
                busyWait(50_000);
            }

            // S4: Network jitter
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(longToBytes(System.nanoTime() - t0));
            md.update(longToBytes(System.currentTimeMillis()));
            byte[] s4 = md.digest();

            // S5: Client nonce
            byte[] s5 = new byte[32];
            if (clientNonce != null && clientNonce.length >= 32) {
                System.arraycopy(clientNonce, 0, s5, 0, 32);
            } else {
                secureRandom.nextBytes(s5);
            }

            // XOR all five
            byte[] xored = new byte[32];
            for (int i = 0; i < 32; i++) {
                xored[i] = (byte) (s1[i] ^ s2[i] ^ s3[i] ^ s4[i] ^ s5[i]);
            }

            // Final SHA-256
            md.reset();
            byte[] finalSeed = md.digest(xored);

            String[] sources = insectActive
                    ? new String[]{"TRNG", "INSECT", "THERMAL", "JITTER", "CLIENT"}
                    : new String[]{"TRNG", "INSECT_FALLBACK", "THERMAL", "JITTER", "CLIENT"};
            lastActiveSources.set(sources);

            String seedHex = HexFormat.of().formatHex(finalSeed);
            log.debug("Seed: {}... sources={}", seedHex.substring(0, 12), String.join("+", sources));

            return new EntropyResult(finalSeed, seedHex, sources,
                    reading != null ? reading.count() : 0, insectActive);

        } catch (Exception e) {
            log.error("Entropy generation failed, using SecureRandom fallback", e);
            byte[] fallback = new byte[32];
            secureRandom.nextBytes(fallback);
            return new EntropyResult(fallback, HexFormat.of().formatHex(fallback),
                    new String[]{"TRNG_FALLBACK"}, 0, false);
        }
    }

    public void updateInsectReading(InsectReading reading) {
        lastInsectReading.set(reading);
        insectCount.set(reading.count());
        lastInsectUpdateMs.set(System.currentTimeMillis());
    }

    public InsectReading getLastInsectReading() { return lastInsectReading.get(); }
    public int getCurrentInsectCount() { return insectCount.get(); }
    public int getMinInsectCount() { return minInsectCount; }
    public String[] getLastActiveSources() { return lastActiveSources.get(); }

    private InsectReading fetchInsectReading() {
        try {
            var response = sidecarClient.get()
                    .uri("/insect/reading")
                    .retrieve()
                    .bodyToMono(InsectReading.class)
                    .timeout(Duration.ofMillis(500))
                    .block();
            if (response != null) updateInsectReading(response);
            return response;
        } catch (Exception e) {
            // Use cached reading if fresh (< 2 seconds old)
            long age = System.currentTimeMillis() - lastInsectUpdateMs.get();
            InsectReading cached = lastInsectReading.get();
            if (cached != null && age < 2000) {
                log.debug("Sidecar slow, using cached reading (age={}ms)", age);
                return cached;
            }
            log.warn("Sidecar unavailable (S2 fallback): {}", e.getMessage());
            return null;
        }
    }

    private byte[] hashInsectReading(InsectReading reading) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        if (reading == null || reading.insects() == null || reading.insects().isEmpty()) {
            byte[] fallback = new byte[32];
            secureRandom.nextBytes(fallback);
            return fallback;
        }
        StringBuilder sb = new StringBuilder();
        for (var insect : reading.insects()) {
            sb.append(insect.x()).append(",").append(insect.y()).append(";");
        }
        sb.append(reading.capturedAtNs());
        return md.digest(sb.toString().getBytes());
    }

    private byte[] longToBytes(long value) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putLong(value);
        return buf.array();
    }

    private void busyWait(long nanos) {
        long end = System.nanoTime() + nanos;
        //noinspection StatementWithEmptyBody
        while (System.nanoTime() < end) {}
    }

    public record EntropyResult(
            byte[] seedBytes, String seedHex, String[] sourcesActive,
            int insectCount, boolean insectActive) {}

    public record InsectReading(
            List<InsectCoord> insects, int count,
            long capturedAtNs, String frameId) {}

    public record InsectCoord(double x, double y, double confidence) {}
}
