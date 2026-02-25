# PROJECT NIDHI — Complete Agent Build Specification
## Version 1.0 | Hackathon Edition | 36-Hour Delivery Target

---

## HOW TO READ THIS DOCUMENT

This specification is written for an autonomous agent with filesystem, bash, and code execution capabilities. Every section is either:

- `[EXECUTE]` — Run this exact command
- `[CREATE FILE]` — Write this exact content to this exact path
- `[DECISION]` — A branching condition the agent must evaluate
- `[VERIFY]` — A check the agent must pass before proceeding
- `[EXPLAIN]` — Context the agent needs to make correct decisions

No section is optional. Execute in order. Do not skip verifications.

---

## SECTION 0: PROJECT OVERVIEW

### What This Is

Project Nidhi is a last-mile digital banking backend for rural India. It enables voice-based financial transactions for users with low literacy on low-end devices and 2G networks. The security model uses a biological-hybrid entropy system: insect movement (tracked via YOLOv8 computer vision) contributes to a five-source cryptographic entropy pool.

### What Gets Built

```
nidhi-backend/                          ← Spring Boot 3.2 (Java 17)
├── pom.xml
├── README.md
├── src/
│   └── main/
│       ├── java/com/nidhi/
│       │   ├── NidhiApplication.java
│       │   ├── common/
│       │   │   ├── CorsConfig.java
│       │   │   └── WebClientConfig.java
│       │   ├── entropy/
│       │   │   ├── EntropyPool.java
│       │   │   └── EntropyController.java
│       │   ├── challenge/
│       │   │   └── ChallengeEngine.java
│       │   ├── voice/
│       │   │   ├── BhashiniClient.java
│       │   │   ├── IntentParser.java
│       │   │   ├── VoiceService.java
│       │   │   └── VoiceController.java
│       │   ├── transaction/
│       │   │   ├── TransactionService.java
│       │   │   └── TransactionController.java
│       │   └── dashboard/
│       │       ├── AuditLog.java
│       │       └── DashboardController.java
│       └── resources/
│           ├── application.properties
│           └── static/
│               └── dashboard.html
└── insect_sidecar/                     ← Python 3.11 (Flask + YOLOv8)
    ├── main.py
    └── requirements.txt
```

### Runtime Architecture

```
[Mobile App] ──HTTP──► [Spring Boot :8080]
                              │
                    ┌─────────┼──────────┐
                    ▼         ▼          ▼
              VoiceService  ChallengeEngine  AuditLog
                    │              │
              BhashiniAPI    EntropyPool
              (external)          │
                           ┌──────┼──────┐
                           ▼      ▼      ▼
                         TRNG  [Python  Thermal
                               Sidecar  Noise
                               :5001]
                                   │
                              YOLOv8 + Webcam

[Browser] ──HTTP──► [GET /dashboard.html]
                    [SSE /entropy/live   ]
                    [GET /dashboard/audit]
```

### Technology Decisions (non-negotiable)

| Component | Technology | Reason |
|---|---|---|
| Backend framework | Spring Boot 3.2 | Team comfort. REST + SSE built-in. |
| Language | Java 17 | Records, modern switch, no boilerplate |
| HTTP client | Spring WebFlux WebClient | Async, timeout support |
| Crypto | Java stdlib only | No extra deps. SHA-256, ECDSA, SecureRandom built-in. |
| Entropy vision | Python + YOLOv8 nano + OpenCV | Fastest YOLO variant, CPU-viable |
| Vision API bridge | Local Flask HTTP :5001 | Simplest Spring→Python bridge |
| NLP / ASR | Bhashini ULCA API | Free, 22 Indian languages, government-backed |
| Storage | ConcurrentHashMap (in-memory) | No DB setup time. Survives 36hr hackathon. |
| Dashboard UI | Vanilla HTML/JS, SSE | Zero build tools. Served by Spring static. |

---

## SECTION 1: ENVIRONMENT SETUP

### 1.1 Verify Prerequisites

[EXECUTE]
```bash
java -version          # Must be 17+
mvn -version           # Must be 3.8+
python3 --version      # Must be 3.9+
pip3 --version
```

[DECISION] If Java < 17: install Amazon Corretto 17 or Eclipse Temurin 17.
[DECISION] If Maven missing: install via `sdk install maven` or OS package manager.

### 1.2 Create Project Root

[EXECUTE]
```bash
mkdir -p nidhi-backend/src/main/java/com/nidhi/{common,entropy,challenge,voice,transaction,dashboard}
mkdir -p nidhi-backend/src/main/resources/static
mkdir -p nidhi-backend/insect_sidecar
cd nidhi-backend
```

### 1.3 Python Dependencies

[EXECUTE]
```bash
cd insect_sidecar
pip3 install flask==3.0.0 ultralytics==8.0.238 opencv-python==4.8.1.78 numpy==1.26.2
```

[VERIFY] Run: `python3 -c "from ultralytics import YOLO; print('YOLO OK')"` — must print `YOLO OK`.

[EXECUTE] Download YOLOv8 nano model:
```bash
python3 -c "from ultralytics import YOLO; YOLO('yolov8n.pt')"
# Downloads ~6MB model to current dir
```

[VERIFY] File `yolov8n.pt` exists in `insect_sidecar/`.

### 1.4 Bhashini API Registration

[EXPLAIN] Bhashini is a Government of India NLP platform at https://bhashini.gov.in.
It provides free ASR (speech-to-text) and translation for 22 Indian languages.
Registration takes ~10 minutes but API key approval can take up to 2 hours.
Do this step BEFORE the hackathon clock starts.

After registration, you will have:
- `BHASHINI_API_KEY` — a long token string
- `BHASHINI_PIPELINE_ID` — a UUID for the inference pipeline

These go into `application.properties`. Keep them as environment variables:
```bash
export BHASHINI_API_KEY="your-key-here"
export BHASHINI_PIPELINE_ID="your-pipeline-id-here"
```

---

## SECTION 2: SPRING BOOT PROJECT FILES

### 2.1 pom.xml

[CREATE FILE: `nidhi-backend/pom.xml`]
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.nidhi</groupId>
    <artifactId>nidhi-backend</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- REST controllers, SseEmitter, static file serving -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- WebClient for calling Bhashini and Python sidecar -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- @Valid, @NotBlank etc. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Lombok: eliminates getter/setter/constructor boilerplate -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2.2 application.properties

[CREATE FILE: `nidhi-backend/src/main/resources/application.properties`]
```properties
server.port=8080

# Bhashini API — register at bhashini.gov.in
# Use environment variables: export BHASHINI_API_KEY=...
bhashini.api.url=https://dhruva-api.bhashini.gov.in
bhashini.api.key=${BHASHINI_API_KEY:REPLACE_ME}
bhashini.pipeline.id=${BHASHINI_PIPELINE_ID:REPLACE_ME}

# Python sidecar (insect entropy)
insect.sidecar.url=http://localhost:5001

# Challenge engine timing
# Outer TTL: how long the challenge seed is valid
challenge.seed.ttl.ms=5000
# Inner TTL: how fast the client must respond (logged only, not hard-rejected in demo)
challenge.response.ttl.ms=500
# Lockout after this many consecutive failures
challenge.max.failures=3

# Minimum insects detected before marking biological source as active
entropy.insect.min.count=2

# Maximum transaction value: 1,000,000 paise = Rs 10,000
transaction.max.amount.paise=1000000

# Audit log entries to keep in memory
audit.log.max.entries=100

logging.level.com.nidhi=DEBUG
logging.level.org.springframework.web=INFO
```

---

## SECTION 3: JAVA SOURCE FILES

### 3.1 NidhiApplication.java

[CREATE FILE: `src/main/java/com/nidhi/NidhiApplication.java`]

[EXPLAIN] `@EnableScheduling` is mandatory. Without it, the `@Scheduled` cleanup
in ChallengeEngine never runs and expired challenges stay in memory forever,
making the TTL mechanism non-functional.

```java
package com.nidhi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NidhiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NidhiApplication.class, args);
    }
}
```

### 3.2 CorsConfig.java

[CREATE FILE: `src/main/java/com/nidhi/common/CorsConfig.java`]

[EXPLAIN] Mobile app and backend run on different IPs during the hackathon
(same WiFi, different origins). Without this config, every API call from
the mobile app is blocked by the browser/OS with a CORS error.
`allowedOrigins("*")` is acceptable for a demo environment.

```java
package com.nidhi.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
```

### 3.3 WebClientConfig.java

[CREATE FILE: `src/main/java/com/nidhi/common/WebClientConfig.java`]

[EXPLAIN] Two separate WebClient beans:
- `bhashiniWebClient`: pre-configured with Bhashini base URL and 10MB codec limit
  (audio base64 payloads can be large)
- `sidecarWebClient`: pre-configured for localhost:5001 Python process

Each service `@Autowires` the client it needs by qualifier name.

```java
package com.nidhi.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("bhashiniWebClient")
    public WebClient bhashiniWebClient(
            @Value("${bhashini.api.url}") String bhashiniUrl) {
        return WebClient.builder()
                .baseUrl(bhashiniUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Bean("sidecarWebClient")
    public WebClient sidecarWebClient(
            @Value("${insect.sidecar.url}") String sidecarUrl) {
        return WebClient.builder()
                .baseUrl(sidecarUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
```

### 3.4 EntropyPool.java

[CREATE FILE: `src/main/java/com/nidhi/entropy/EntropyPool.java`]

[EXPLAIN — CRITICAL DESIGN DECISIONS IN THIS FILE]

**The Five Sources:**
- S1 `SecureRandom`: Java's TRNG interface. On Linux uses `/dev/urandom` backed by kernel entropy pool. On hardware with TPM, uses hardware RNG. Always available. Always strong.
- S2 `Insect coordinates`: Fetched from Python sidecar via HTTP. The XY coordinates of detected insects/motion are concatenated as "x1,y1;x2,y2;timestamp" and SHA-256 hashed. If the sidecar is unreachable (timeout 300ms), falls back to additional SecureRandom bytes. The system continues operating normally.
- S3 `Thermal/timing noise`: `System.nanoTime()` sampled four times with 50µs busy-waits between samples. Lower bits of nanoTime are influenced by CPU cache state, OS scheduler interrupts, and hardware timer jitter. Not cryptographically strong alone but adds real physical non-determinism when XORed with S1.
- S4 `Network jitter`: The elapsed nanoseconds of the sidecar HTTP call itself. This reflects OS network stack latency, kernel scheduling variance, and local network timing. Hashed with current system time.
- S5 `Client nonce`: 32 bytes contributed by the mobile app for this session. Means even a fully compromised server cannot predict the seed without also knowing the client's contribution. If absent, falls back to SecureRandom.

**Why XOR then SHA-256:**
If ANY single source Si is truly random, then XOR(S1..S5) is indistinguishable from random even if all other sources are controlled. SHA-256 post-processing removes any statistical bias from the XOR and produces exactly 32 bytes of uniform output.

**Why `updateInsectReading()` is public:**
The EntropyController's SSE loop polls the sidecar every 500ms independently of seed generation. It calls this method to keep the cached reading fresh. This means seed generation gets recent coordinates even between transactions.

```java
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

    // Cached insect state — updated by SSE loop every 500ms
    private final AtomicReference<InsectReading> lastInsectReading = new AtomicReference<>();
    private final AtomicInteger insectCount = new AtomicInteger(0);
    private final AtomicLong lastInsectUpdateMs = new AtomicLong(0);
    private final AtomicReference<String[]> lastActiveSources =
            new AtomicReference<>(new String[]{});

    public EntropyPool(@Qualifier("sidecarWebClient") WebClient sidecarClient) {
        this.sidecarClient = sidecarClient;
    }

    /**
     * Generate a fresh 256-bit entropy seed.
     * Called by ChallengeEngine on every challenge issuance.
     * Blocks up to ~400ms (sidecar timeout + processing).
     */
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

            // S4: Network jitter (latency of sidecar call as entropy)
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
    public String[] getLastActiveSources() { return lastActiveSources.get(); }

    // ── Private helpers ───────────────────────────────────────────

    private InsectReading fetchInsectReading() {
        try {
            var response = sidecarClient.get()
                    .uri("/insect/reading")
                    .retrieve()
                    .bodyToMono(InsectReading.class)
                    .timeout(Duration.ofMillis(300))
                    .block();
            if (response != null) updateInsectReading(response);
            return response;
        } catch (Exception e) {
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

    // ── Data records ──────────────────────────────────────────────

    public record EntropyResult(
            byte[] seedBytes, String seedHex, String[] sourcesActive,
            int insectCount, boolean insectActive) {}

    public record InsectReading(
            List<InsectCoord> insects, int count,
            long capturedAtNs, String frameId) {}

    public record InsectCoord(double x, double y, double confidence) {}
}
```

### 3.5 EntropyController.java

[CREATE FILE: `src/main/java/com/nidhi/entropy/EntropyController.java`]

[EXPLAIN — SSE ARCHITECTURE]
`SseEmitter(0L)` creates an emitter with no timeout. The connection stays open
until the browser closes it. For the hackathon demo this is fine.

The `@PostConstruct` SSE loop runs every 500ms regardless of connected clients.
The `removeIf` pattern removes dead connections automatically — if a browser tab
closes, the next push attempt gets an IOException and the emitter is removed.

The sidecar is polled here independently (every 500ms for the SSE stream)
AND inside EntropyPool.generateSeed() (every transaction). This means the
dashboard always has fresh insect data even between transactions.

```java
package com.nidhi.entropy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/entropy")
@RequiredArgsConstructor
public class EntropyController {

    private final EntropyPool entropyPool;

    @Qualifier("sidecarWebClient")
    private final WebClient sidecarClient;

    private final CopyOnWriteArrayList<SseEmitter> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void startSseLoop() {
        // Poll sidecar and push to SSE clients every 500ms
        scheduler.scheduleAtFixedRate(() -> {
            // Poll sidecar for fresh insect data
            try {
                var reading = sidecarClient.get()
                        .uri("/insect/reading")
                        .retrieve()
                        .bodyToMono(EntropyPool.InsectReading.class)
                        .timeout(Duration.ofMillis(400))
                        .block();
                if (reading != null) entropyPool.updateInsectReading(reading);
            } catch (Exception ignored) {}

            if (clients.isEmpty()) return;

            var payload = Map.of(
                    "insectCount", entropyPool.getCurrentInsectCount(),
                    "sourcesActive", entropyPool.getLastActiveSources(),
                    "insects", entropyPool.getLastInsectReading() != null
                            ? entropyPool.getLastInsectReading().insects()
                            : List.of(),
                    "timestamp", System.currentTimeMillis()
            );

            clients.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("entropy-update")
                            .data(payload, MediaType.APPLICATION_JSON));
                    return false;
                } catch (IOException e) {
                    return true; // dead client
                }
            });
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * GET /entropy/live
     * Dashboard subscribes here via JavaScript EventSource.
     * Receives insect coordinate stream every 500ms.
     */
    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveStream() {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(e -> clients.remove(emitter));
        clients.add(emitter);
        log.info("SSE client connected. Total: {}", clients.size());
        return emitter;
    }

    /**
     * GET /entropy/status
     * Quick entropy health check for dashboard header.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "insectCount", entropyPool.getCurrentInsectCount(),
                "sourcesActive", entropyPool.getLastActiveSources(),
                "insectOnline", entropyPool.getCurrentInsectCount() >= 2,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * GET /health
     * Used by mobile app to verify backend is reachable.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "nidhi-backend"));
    }
}
```

### 3.6 ChallengeEngine.java

[CREATE FILE: `src/main/java/com/nidhi/challenge/ChallengeEngine.java`]

[EXPLAIN — SECURITY DESIGN DECISIONS]

**Keypair per device (demo simulation of TEE):**
In production, the mobile device's TEE (Trusted Execution Environment) holds an
ECDSA private key that never leaves hardware. For this demo:
1. On first use of a deviceId, `KeyPairGenerator` creates a P-256 ECDSA keypair
2. Server keeps the PUBLIC key
3. Server sends the PRIVATE key to the mobile app (in `ChallengePacket.privateKeyBase64`)
4. Mobile app uses the private key to sign the challenge

This is marked `// DEMO ONLY` throughout. In a production system, step 3 never
happens — the private key is generated inside the TEE and cannot be exported.

**What gets signed:**
`signingPayload = seedHex + "|" + sha256(txPayload) + "|" + counter`

This binds the signature to:
- The specific entropy seed (prevents pre-computation)
- The transaction contents (prevents amount/recipient substitution)
- The monotonic counter (prevents replay)

**Monotonic counter:**
`deviceCounters` maps deviceId → last accepted counter value.
On each new challenge: `counter = deviceCounters.merge(deviceId, 1L, Long::sum)`
On verification: reject if `response.counter <= storedCounter`.
An attacker replaying an old packet has a counter that is ≤ the current stored
value and will be rejected.

**Dual TTL:**
- Outer TTL (5000ms): the challenge expires 5 seconds after issuance
- Inner TTL (500ms): the expected response time. In hackathon mode, we LOG
  violations but don't hard-reject them. This avoids 2G latency issues during demo.

**`@Scheduled(fixedDelay = 500)` cleanup:**
Runs every 500ms. Iterates all pending challenges and removes expired ones.
This is the "seed erasure" mechanism — once removed, the seed cannot be
recovered or used. There is no way to verify a response against an expired seed.

```java
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

    // Key: txId → pending challenge
    private final ConcurrentHashMap<String, PendingChallenge> pending = new ConcurrentHashMap<>();
    // Key: deviceId → last accepted counter
    private final ConcurrentHashMap<String, Long> deviceCounters = new ConcurrentHashMap<>();
    // Key: deviceId → ECDSA key pair (demo simulation of TEE)
    private final ConcurrentHashMap<String, KeyPair> deviceKeys = new ConcurrentHashMap<>();
    // Key: deviceId → consecutive failure count
    private final ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    // Locked device IDs
    private final Set<String> locked = ConcurrentHashMap.newKeySet();

    /**
     * Issue a cryptographic challenge for a transaction.
     *
     * @param deviceId       Unique device identifier from mobile app
     * @param txPayload      Serialized transaction: "TRANSFER|recipient|amountPaise|deviceId"
     * @param clientNonceHex Hex-encoded 32-byte client entropy contribution (nullable)
     * @return ChallengePacket containing txId, challenge, signing key (demo only)
     */
    public ChallengePacket issueChallenge(String deviceId, String txPayload, String clientNonceHex) {
        if (locked.contains(deviceId)) {
            throw new SecurityException("Device locked after repeated failures: " + deviceId);
        }

        // Ensure device has registered keypair
        deviceKeys.computeIfAbsent(deviceId, id -> generateKeyPair());

        // Increment counter
        long counter = deviceCounters.merge(deviceId, 1L, Long::sum);

        // Parse client nonce
        byte[] clientNonce = null;
        if (clientNonceHex != null && !clientNonceHex.isBlank()) {
            try { clientNonce = HexFormat.of().parseHex(clientNonceHex); }
            catch (Exception e) { log.warn("Invalid client nonce, ignoring"); }
        }

        // Generate entropy seed
        EntropyPool.EntropyResult entropy = entropyPool.generateSeed(clientNonce);

        // Build signing payload
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

    /**
     * Verify a signed challenge response.
     *
     * Checks (in order):
     * 1. Challenge exists (not expired/cleaned up)
     * 2. Device matches
     * 3. Outer TTL not exceeded
     * 4. ECDSA signature valid
     * 5. All pass → commit
     */
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

        if (!verifySignature(p.signingPayload(), signatureB64, deviceKeys.get(deviceId))) {
            pending.remove(txId);
            recordFailure(deviceId);
            return VerificationResult.fail("INVALID_SIGNATURE", "Signature verification failed.");
        }

        pending.remove(txId);
        resetFailures(deviceId);

        log.info("Challenge verified: txId={} device={} counter={} responseTime={}ms",
                txId, deviceId, p.counter(), responseMs);

        return VerificationResult.success(txId, p.txPayload(), p.seedHex(),
                p.sourcesActive(), p.insectCount(), responseMs);
    }

    /** Remove expired challenges every 500ms. This is the seed erasure mechanism. */
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

    // ── Private helpers ───────────────────────────────────────────

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

    // ── Records ───────────────────────────────────────────────────

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
```

### 3.7 BhashiniClient.java

[CREATE FILE: `src/main/java/com/nidhi/voice/BhashiniClient.java`]

[EXPLAIN — BHASHINI API]

Bhashini uses a pipeline-based API. Each call specifies a list of `pipelineTasks`
with a `taskType` ("asr", "translation", "tts") and returns `pipelineResponse`.

**ASR endpoint:** Accepts base64 audio, returns transcribed text in source language.
Audio must be WAV format, 16kHz sample rate, mono channel.
The mobile app must record in this format or convert before sending.

**Translation endpoint:** Takes text + source language + target language.
Returns translated text. We use this to normalize all input to English
before passing to IntentParser.

**Fallback strategy:** ALL Bhashini calls are wrapped in try-catch with an
8-second timeout. If Bhashini is slow or unavailable:
- ASR failure: caller falls back to text input
- Translation failure: returns original text (IntentParser tries to handle it)
- Confirmation text failure: returns English version

This means Bhashini outages never prevent a transaction from being attempted.

**Language codes (ISO 639-1):**
hi=Hindi, te=Telugu, ta=Tamil, kn=Kannada, bn=Bengali,
mr=Marathi, gu=Gujarati, pa=Punjabi, or=Odia, en=English

```java
package com.nidhi.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BhashiniClient {

    private final WebClient client;

    @Value("${bhashini.api.key}") private String apiKey;
    @Value("${bhashini.pipeline.id}") private String pipelineId;

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final String INFERENCE_PATH = "/services/inference/pipeline";

    public BhashiniClient(@Qualifier("bhashiniWebClient") WebClient client) {
        this.client = client;
    }

    /**
     * Convert audio to text using Bhashini ASR.
     *
     * @param audioBase64    Base64-encoded WAV audio (16kHz mono)
     * @param sourceLanguage ISO language code ("hi", "te", etc.)
     * @return Transcribed text, or null on failure
     */
    public String transcribeAudio(String audioBase64, String sourceLanguage) {
        try {
            var body = Map.of(
                    "pipelineTasks", List.of(Map.of(
                            "taskType", "asr",
                            "config", Map.of(
                                    "language", Map.of("sourceLanguage", sourceLanguage),
                                    "audioFormat", "wav",
                                    "samplingRate", 16000))),
                    "inputData", Map.of(
                            "audio", List.of(Map.of("audioContent", audioBase64))));

            var response = client.post()
                    .uri(INFERENCE_PATH)
                    .header("Authorization", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            return extractText(response, "source");
        } catch (Exception e) {
            log.error("Bhashini ASR failed [{}]: {}", sourceLanguage, e.getMessage());
            return null;
        }
    }

    /**
     * Translate text to English.
     * Returns original text if translation fails (IntentParser handles English and partial cases).
     */
    public String translateToEnglish(String text, String sourceLanguage) {
        if ("en".equals(sourceLanguage)) return text;
        try {
            var body = Map.of(
                    "pipelineTasks", List.of(Map.of(
                            "taskType", "translation",
                            "config", Map.of(
                                    "language", Map.of(
                                            "sourceLanguage", sourceLanguage,
                                            "targetLanguage", "en")))),
                    "inputData", Map.of(
                            "input", List.of(Map.of("source", text))));

            var response = client.post()
                    .uri(INFERENCE_PATH)
                    .header("Authorization", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            String translated = extractText(response, "target");
            return translated != null ? translated : text;
        } catch (Exception e) {
            log.warn("Bhashini translation failed, returning original: {}", e.getMessage());
            return text;
        }
    }

    /**
     * Generate confirmation text in the user's language.
     * Example: "Send Rs 500 to Ramu?" → "रामू को पाँच सौ रुपये भेजना है?"
     *
     * @param recipient      Recipient name or phone number
     * @param amountPaise    Amount in paise (integer, Rs 1 = 100 paise)
     * @param targetLanguage Target ISO language code
     * @return Confirmation string in target language (English fallback on error)
     */
    public String generateConfirmationText(String recipient, long amountPaise, String targetLanguage) {
        long rupees = amountPaise / 100;
        long paise  = amountPaise % 100;
        String english = paise > 0
                ? String.format("Send %d rupees and %d paise to %s?", rupees, paise, recipient)
                : String.format("Send %d rupees to %s?", rupees, recipient);

        if ("en".equals(targetLanguage)) return english;

        try {
            var body = Map.of(
                    "pipelineTasks", List.of(Map.of(
                            "taskType", "translation",
                            "config", Map.of(
                                    "language", Map.of(
                                            "sourceLanguage", "en",
                                            "targetLanguage", targetLanguage)))),
                    "inputData", Map.of(
                            "input", List.of(Map.of("source", english))));

            var response = client.post()
                    .uri(INFERENCE_PATH)
                    .header("Authorization", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            String translated = extractText(response, "target");
            return translated != null ? translated : english;
        } catch (Exception e) {
            log.warn("Confirmation translation failed, using English: {}", e.getMessage());
            return english;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> response, String field) {
        try {
            var pipelineResponse = (List<?>) response.get("pipelineResponse");
            var firstTask = (Map<?, ?>) pipelineResponse.get(0);
            var output = (List<?>) firstTask.get("output");
            var first = (Map<?, ?>) output.get(0);
            return (String) first.get(field);
        } catch (Exception e) {
            log.error("Failed to parse Bhashini response: {}", e.getMessage());
            return null;
        }
    }
}
```

### 3.8 IntentParser.java

[CREATE FILE: `src/main/java/com/nidhi/voice/IntentParser.java`]

[EXPLAIN — WHY REGEX NOT ML]

The domain of banking voice commands is narrow and predictable:
- "send/transfer/pay/give [amount] rupees to [recipient]"
- "check balance"

A regex + word-to-number approach handles 95% of real cases with:
- Zero model download time
- Zero inference latency
- Zero accuracy variance
- Zero dependency

An ML intent classifier (Rasa, Wit.ai, Dialogflow) would add 2-4 hours
of setup for 4% more coverage. Wrong tradeoff for 36 hours.

**Amount parsing strategy:**
1. Try digit amount first: look for numbers (including Indian comma format 1,00,000)
2. If no digits, try word amount: "fifty" → 50, "five hundred" → 500, "two lakh" → 200000
3. Convert final figure to paise (* 100)

**Recipient extraction:**
1. Phone number pattern first (10 digits starting 6-9)
2. "to [Name]" pattern: captures 2-30 chars after "to"

**Confidence scoring:**
- 0.85 if digit amount found
- 0.75 if word amount found
- +0.10 bonus if phone number (more unambiguous than a name)
- +0.05 bonus if name found via "to" pattern
Threshold for actionable: confidence >= 0.5

```java
package com.nidhi.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class IntentParser {

    private static final Set<String> TRANSFER_WORDS = Set.of(
            "send", "transfer", "pay", "give", "remit", "deposit", "bhejo", "daalo");

    private static final Pattern DIGIT_AMOUNT = Pattern.compile(
            "(?:rs\\.?|rupees?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|rupees?|paise?)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE = Pattern.compile("\\b([6-9]\\d{9})\\b");

    private static final Pattern RECIPIENT = Pattern.compile(
            "\\bto\\s+([a-zA-Z][a-zA-Z\\s]{1,30}|[6-9]\\d{9})",
            Pattern.CASE_INSENSITIVE);

    // Ordered map: longest keys first to avoid partial matches
    private static final LinkedHashMap<String, Long> WORDS = new LinkedHashMap<>() {{
        put("nineteen", 19L); put("eighteen", 18L); put("seventeen", 17L);
        put("sixteen", 16L); put("fifteen", 15L); put("fourteen", 14L);
        put("thirteen", 13L); put("twelve", 12L); put("eleven", 11L);
        put("crore", 10_000_000L); put("lakh", 100_000L); put("lakhs", 100_000L);
        put("thousand", 1_000L); put("hundred", 100L);
        put("ninety", 90L); put("eighty", 80L); put("seventy", 70L);
        put("sixty", 60L); put("fifty", 50L); put("forty", 40L);
        put("thirty", 30L); put("twenty", 20L); put("ten", 10L);
        put("nine", 9L); put("eight", 8L); put("seven", 7L); put("six", 6L);
        put("five", 5L); put("four", 4L); put("three", 3L); put("two", 2L);
        put("one", 1L); put("zero", 0L);
    }};

    /**
     * Parse English text into a structured transaction intent.
     * Input should be English (after Bhashini translation if needed).
     */
    public ParsedIntent parse(String text) {
        if (text == null || text.isBlank())
            return ParsedIntent.unknown("Empty input");

        String lower = text.toLowerCase().trim();
        log.debug("Parsing: '{}'", lower);

        // ── Determine intent type ─────────────────────────────────
        IntentType type = IntentType.UNKNOWN;
        for (String w : TRANSFER_WORDS) {
            if (lower.contains(w)) { type = IntentType.TRANSFER; break; }
        }
        if (lower.contains("balance") || lower.contains("how much"))
            type = IntentType.BALANCE_CHECK;
        if (type == IntentType.UNKNOWN)
            return ParsedIntent.unknown("Cannot determine intent. Say: 'send 500 rupees to [name]'");
        if (type == IntentType.BALANCE_CHECK)
            return new ParsedIntent(IntentType.BALANCE_CHECK, null, 0, 0.9, null);

        // ── Extract amount ────────────────────────────────────────
        long amountPaise = 0;
        double confidence = 0.0;

        Matcher dm = DIGIT_AMOUNT.matcher(lower);
        if (dm.find()) {
            try {
                double amount = Double.parseDouble(dm.group(1).replace(",", ""));
                amountPaise = Math.round(amount * 100);
                confidence = 0.85;
            } catch (NumberFormatException ignored) {}
        }

        if (amountPaise == 0) {
            long wordAmount = parseWordAmount(lower);
            if (wordAmount > 0) {
                amountPaise = wordAmount * 100;
                confidence = 0.75;
            }
        }

        // ── Extract recipient ─────────────────────────────────────
        String recipient = null;

        Matcher pm = PHONE.matcher(lower);
        if (pm.find()) {
            recipient = pm.group(1);
            confidence = Math.min(confidence + 0.10, 1.0);
        }

        if (recipient == null) {
            Matcher rm = RECIPIENT.matcher(lower);
            if (rm.find()) {
                recipient = capitalize(rm.group(1).trim());
                confidence = Math.min(confidence + 0.05, 1.0);
            }
        }

        // ── Validate ──────────────────────────────────────────────
        if (amountPaise <= 0)
            return ParsedIntent.unknown("Could not understand amount. Say the number clearly.");
        if (recipient == null)
            return ParsedIntent.unknown("Could not understand recipient. Say 'to [name or number]'.");
        if (amountPaise > 1_000_000)
            return ParsedIntent.unknown("Amount exceeds Rs 10,000 limit.");

        log.info("Parsed: type={} recipient='{}' amount={}p confidence={}",
                type, recipient, amountPaise, confidence);

        return new ParsedIntent(type, recipient, amountPaise, confidence, null);
    }

    private long parseWordAmount(String text) {
        String[] words = text.split("[\\s-]+");
        long total = 0, current = 0;
        for (String word : words) {
            Long val = WORDS.get(word);
            if (val == null) continue;
            if (val == 100) { current = current == 0 ? 100 : current * 100; }
            else if (val >= 1000) { total += (current == 0 ? 1 : current) * val; current = 0; }
            else { current += val; }
        }
        return total + current;
    }

    private String capitalize(String s) {
        return Arrays.stream(s.split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    public enum IntentType { TRANSFER, BALANCE_CHECK, UNKNOWN }

    public record ParsedIntent(IntentType type, String recipient, long amountPaise,
                                double confidence, String errorMessage) {

        static ParsedIntent unknown(String reason) {
            return new ParsedIntent(IntentType.UNKNOWN, null, 0, 0.0, reason);
        }

        public boolean isActionable() {
            return type != IntentType.UNKNOWN && confidence >= 0.5;
        }

        public String formattedAmount() {
            if (amountPaise == 0) return "Rs 0";
            long r = amountPaise / 100, p = amountPaise % 100;
            return p > 0 ? String.format("Rs %d.%02d", r, p) : String.format("Rs %d", r);
        }
    }
}
```

### 3.9 VoiceService.java

[CREATE FILE: `src/main/java/com/nidhi/voice/VoiceService.java`]

[EXPLAIN — TWO INPUT PATHS]

**Audio path (primary):**
Mobile app records voice → sends as base64 WAV → Bhashini ASR → English text → parse

**Text fallback path (demo safety):**
If audio is null/blank, use textFallback directly → translate to English → parse

The text path is critical. Bhashini ASR can be slow or fail. If the mobile
app detects ASR failure, it shows a text field and the user types their intent.
The backend treats both paths identically after this service.

Always ensure the fallback path is tested before the demo.

```java
package com.nidhi.voice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceService {

    private final BhashiniClient bhashini;
    private final IntentParser parser;

    /**
     * Process a voice or text input into a structured transaction intent.
     *
     * Pipeline:
     * 1. Audio → ASR (Bhashini) OR use textFallback
     * 2. Source language text → English (Bhashini translation)
     * 3. English → ParsedIntent (IntentParser regex)
     * 4. ParsedIntent → confirmation text in source language (Bhashini)
     *
     * @param audioBase64    Nullable. Base64 WAV at 16kHz mono.
     * @param textFallback   Nullable. Used when audio is absent.
     * @param languageCode   ISO code: "hi", "te", "ta", "kn", etc.
     * @return VoiceParseResult with all fields needed by TransactionService
     */
    public VoiceParseResult processInput(String audioBase64, String textFallback, String languageCode) {
        String lang = languageCode != null ? languageCode : "hi";
        boolean usedAudio = false;

        // Step 1: Get source text
        String sourceText = null;

        if (audioBase64 != null && !audioBase64.isBlank()) {
            sourceText = bhashini.transcribeAudio(audioBase64, lang);
            if (sourceText != null && !sourceText.isBlank()) {
                usedAudio = true;
                log.info("ASR success [{}]: '{}'", lang, sourceText);
            } else {
                log.warn("ASR returned empty, falling back to text");
            }
        }

        if (sourceText == null || sourceText.isBlank()) {
            sourceText = textFallback;
        }

        if (sourceText == null || sourceText.isBlank()) {
            return VoiceParseResult.error("No input received. Please speak or type your transaction.");
        }

        // Step 2: Translate to English
        String englishText = bhashini.translateToEnglish(sourceText, lang);
        log.debug("Translation [{}→en]: '{}' → '{}'", lang, sourceText, englishText);

        // Step 3: Parse intent
        IntentParser.ParsedIntent intent = parser.parse(englishText);

        if (!intent.isActionable()) {
            return VoiceParseResult.error(intent.errorMessage() != null
                    ? intent.errorMessage()
                    : "Could not understand the transaction. Please try again.");
        }

        // Step 4: Generate confirmation text in source language
        String confirmText = bhashini.generateConfirmationText(
                intent.recipient(), intent.amountPaise(), lang);

        log.info("Voice parse complete: {}  {} → {}",
                intent.type(), intent.formattedAmount(), intent.recipient());

        return new VoiceParseResult(true, sourceText, englishText, intent.type().name(),
                intent.recipient(), intent.amountPaise(), intent.formattedAmount(),
                confirmText, lang, intent.confidence(), usedAudio, null);
    }

    public record VoiceParseResult(
            boolean success,
            String originalText,      // What user said (source language)
            String translatedText,    // English for parsing
            String intentType,        // "TRANSFER" or "BALANCE_CHECK"
            String recipient,         // Name or phone number
            long amountPaise,
            String formattedAmount,   // "Rs 500" display string
            String confirmationText,  // Read back to user in their language
            String language,
            double confidence,
            boolean usedAudio,
            String errorMessage) {

        static VoiceParseResult error(String msg) {
            return new VoiceParseResult(false, null, null, null, null,
                    0, null, null, null, 0.0, false, msg);
        }
    }
}
```

### 3.10 VoiceController.java

[CREATE FILE: `src/main/java/com/nidhi/voice/VoiceController.java`]

```java
package com.nidhi.voice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /voice/parse
 *
 * The mobile app calls this immediately after recording audio.
 * Returns parsed intent + confirmation string for TTS readback.
 *
 * Always returns HTTP 200. Check response.success for parse outcome.
 * This simplifies mobile app error handling.
 */
@Slf4j
@RestController
@RequestMapping("/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    @PostMapping("/parse")
    public ResponseEntity<VoiceService.VoiceParseResult> parse(
            @RequestBody VoiceParseRequest req) {

        log.info("Voice parse: lang={} hasAudio={} hasText={}",
                req.languageCode(),
                req.audioBase64() != null && !req.audioBase64().isBlank(),
                req.textFallback() != null && !req.textFallback().isBlank());

        return ResponseEntity.ok(
                voiceService.processInput(req.audioBase64(), req.textFallback(), req.languageCode()));
    }

    public record VoiceParseRequest(
            String audioBase64,   // Nullable: base64 WAV 16kHz mono
            String textFallback,  // Nullable: plain text if audio absent
            String languageCode   // "hi","te","ta","kn","bn","mr","gu","pa","or","en"
    ) {}
}
```

### 3.11 AuditLog.java

[CREATE FILE: `src/main/java/com/nidhi/dashboard/AuditLog.java`]

[EXPLAIN — MERKLE CHAIN CONSTRUCTION]

Each entry contains:
- `previousHash`: the `entryHash` of the previous entry (genesis = 64 zeros)
- `entryHash`: SHA-256 of (sequence + type + txId + actor + details + timestamp + previousHash)

To verify integrity: recompute each entry's hash from its fields and check it matches
`entryHash`. Then verify that each entry's `previousHash` matches the previous entry's
`entryHash`. Any discrepancy reveals tampering.

For the demo, the dashboard shows each entry's hash prefix and its link to the
previous hash. This is the visual "tamper-evident log" talking point.

CopyOnWriteArrayList is thread-safe for iteration but uses synchronized append()
to ensure the previous hash is computed correctly under concurrent access.

```java
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

    // ── Public logging methods ────────────────────────────────────

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

    /** Get last N entries, newest first (for dashboard display). */
    public List<AuditEntry> getRecentEntries(int n) {
        var all = new ArrayList<>(entries);
        Collections.reverse(all);
        return all.subList(0, Math.min(n, all.size()));
    }

    public List<AuditEntry> getAllEntries() {
        return Collections.unmodifiableList(entries);
    }

    // ── Private append (synchronized for hash chain integrity) ───

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

    // ── Records ───────────────────────────────────────────────────

    public enum EventType {
        CHALLENGE_ISSUED, CHALLENGE_VERIFIED, CHALLENGE_FAILED, TRANSACTION_COMMITTED
    }

    public record AuditEntry(int sequence, EventType type, String txId, String actor,
                              String details, boolean success, String timestamp,
                              String previousHash, String entryHash) {}
}
```

### 3.12 TransactionService.java

[CREATE FILE: `src/main/java/com/nidhi/transaction/TransactionService.java`]

[EXPLAIN — ORCHESTRATION ONLY]

TransactionService is intentionally thin. It contains no cryptography, no NLP,
no security logic. It only calls the correct services in the correct order and
ensures the audit log captures every step.

**Transaction payload format:**
`"TRANSFER|<recipient>|<amountPaise>|<deviceId>"`

This simple pipe-delimited string is what gets hashed and included in the
challenge. Keeping it human-readable makes debugging easy during the hackathon.
On the confirm path, we split this string to recover recipient and amount.

**Mock ledger:**
`referenceNumber = "NID-" + (System.currentTimeMillis() % 100000)`

This generates a 5-digit reference number that looks like a real transaction
reference (NID-42891). In a production system, this would be a call to
IMPS/UPI/core banking API. Be explicit with judges: "This is where we would
call the payment rail. For the demo, we generate a reference number."

```java
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

    /**
     * Step 1: Initiate a transaction from voice/text input.
     *
     * Called by mobile app after user speaks.
     * Returns challenge packet + confirmation text for TTS.
     */
    public InitiateResponse initiate(InitiateRequest req) {
        // Parse voice input
        VoiceService.VoiceParseResult voice = voiceService.processInput(
                req.audioBase64(), req.textFallback(), req.languageCode());

        if (!voice.success()) {
            return InitiateResponse.error(voice.errorMessage());
        }

        // Build transaction payload
        String txPayload = String.format("TRANSFER|%s|%d|%s",
                voice.recipient(), voice.amountPaise(), req.deviceId());

        // Issue challenge
        ChallengeEngine.ChallengePacket challenge = challengeEngine.issueChallenge(
                req.deviceId(), txPayload, req.clientNonceHex());

        // Audit
        auditLog.logChallengeIssued(challenge.txId(), req.deviceId(),
                challenge.seedHex(), challenge.sourcesActive(), 0);

        log.info("Initiated: txId={} recipient='{}' amount={}",
                challenge.txId(), voice.recipient(), voice.formattedAmount());

        return new InitiateResponse(true, challenge.txId(), voice.recipient(),
                voice.amountPaise(), voice.formattedAmount(), voice.confirmationText(),
                voice.language(), challenge.signingPayload(), challenge.expiresAtMs(),
                challenge.privateKeyBase64(), challenge.sourcesActive(), null);
    }

    /**
     * Step 2: Confirm a transaction with a signed challenge response.
     *
     * Called by mobile app after user confirms and the app signs the challenge.
     * Returns reference number on success.
     */
    public ConfirmResponse confirm(String txId, String deviceId, String signatureB64) {
        ChallengeEngine.VerificationResult result =
                challengeEngine.verify(txId, deviceId, signatureB64);

        if (!result.success()) {
            auditLog.logChallengeFailed(txId, deviceId, result.errorCode());
            return ConfirmResponse.error(result.errorCode(), result.errorMessage());
        }

        // Parse recipient and amount from stored tx payload
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

    // ── Request / Response records ────────────────────────────────

    public record InitiateRequest(
            String audioBase64,    // nullable
            String textFallback,   // nullable if audio present
            String languageCode,
            String deviceId,
            String clientNonceHex  // nullable
    ) {}

    public record InitiateResponse(
            boolean success,
            String txId,
            String recipient,
            long amountPaise,
            String formattedAmount,
            String confirmationText,  // Read to user via TTS
            String language,
            String signingPayload,    // Mobile app must sign this
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
```

### 3.13 TransactionController.java

[CREATE FILE: `src/main/java/com/nidhi/transaction/TransactionController.java`]

[EXPLAIN — SIGNING INSTRUCTIONS FOR MOBILE APP TEAMMATE]

After `/transaction/initiate` returns, the mobile app must:

```java
// Android Java
byte[] privateKeyBytes = Base64.decode(response.privateKeyBase64, Base64.DEFAULT);
PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(spec);

Signature sig = Signature.getInstance("SHA256withECDSA");
sig.initSign(privateKey);
sig.update(response.signingPayload.getBytes(StandardCharsets.UTF_8));
byte[] sigBytes = sig.sign();
String signatureBase64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP);

// Then POST /transaction/confirm with txId, deviceId, signatureBase64
```

```kotlin
// Android Kotlin
val privateKeyBytes = Base64.decode(response.privateKeyBase64, Base64.DEFAULT)
val spec = PKCS8EncodedKeySpec(privateKeyBytes)
val privateKey = KeyFactory.getInstance("EC").generatePrivate(spec)

val sig = Signature.getInstance("SHA256withECDSA")
sig.initSign(privateKey)
sig.update(response.signingPayload.toByteArray(Charsets.UTF_8))
val signatureBase64 = Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
```

```java
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
     *
     * Success response includes: txId, confirmationText, signingPayload, privateKeyBase64
     * Error response: success=false, errorMessage set
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
     *
     * Success response includes: referenceNumber, formattedAmount, recipient, responseTimeMs
     * Error response: success=false, errorCode, errorMessage
     */
    @PostMapping("/confirm")
    public ResponseEntity<TransactionService.ConfirmResponse> confirm(
            @RequestBody ConfirmRequest req) {
        log.info("Confirm: txId={} device={}", req.txId(), req.deviceId());
        return ResponseEntity.ok(txService.confirm(req.txId(), req.deviceId(), req.signatureBase64()));
    }

    public record ConfirmRequest(String txId, String deviceId, String signatureBase64) {}
}
```

### 3.14 DashboardController.java

[CREATE FILE: `src/main/java/com/nidhi/dashboard/DashboardController.java`]

```java
package com.nidhi.dashboard;

import com.nidhi.entropy.EntropyPool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Serves dashboard data APIs.
 *
 * The dashboard HTML itself is at src/main/resources/static/dashboard.html
 * and is served automatically by Spring's static resource handler.
 * Access it at: http://localhost:8080/dashboard.html
 *
 * This controller provides the JSON data the dashboard JS polls.
 */
@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final AuditLog auditLog;
    private final EntropyPool entropyPool;

    /**
     * GET /dashboard/audit
     * Polled by dashboard every 2 seconds.
     * Returns last 20 audit entries + current entropy status.
     */
    @GetMapping("/dashboard/audit")
    public ResponseEntity<Map<String, Object>> getAuditData() {
        return ResponseEntity.ok(Map.of(
                "entries", auditLog.getRecentEntries(20),
                "totalEntries", auditLog.getAllEntries().size(),
                "insectCount", entropyPool.getCurrentInsectCount(),
                "sourcesActive", entropyPool.getLastActiveSources(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * GET /health
     * Liveness probe. Mobile app pings this on startup.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "nidhi-backend",
                "insectOnline", entropyPool.getCurrentInsectCount() >= 2,
                "insectCount", entropyPool.getCurrentInsectCount(),
                "timestamp", System.currentTimeMillis()
        ));
    }
}
```

---

## SECTION 4: PYTHON SIDECAR

### 4.1 insect_sidecar/requirements.txt

[CREATE FILE: `nidhi-backend/insect_sidecar/requirements.txt`]
```
flask==3.0.0
ultralytics==8.0.238
opencv-python==4.8.1.78
numpy==1.26.2
```

### 4.2 insect_sidecar/main.py

[CREATE FILE: `nidhi-backend/insect_sidecar/main.py`]

[EXPLAIN — DUAL DETECTION STRATEGY]

**Frame differencing (primary):**
Compares current frame to previous frame pixel-by-pixel. Any moving region
creates a bright diff. We threshold and find contours to locate bounding boxes.
This detects ANY movement — insects, dust, vibration — regardless of species.
Works for tiny ants that YOLOv8 cannot classify (too small for COCO training).

**YOLOv8n (secondary, every 5th frame):**
Runs full object detection. Adds detected entities from COCO categories (animals,
objects). Catches larger insects, mice, fingers — anything visible.

**Why every 5th frame for YOLO:**
YOLOv8n on CPU takes ~80-200ms per inference on a laptop. Running every frame
would cap FPS at ~5-10. By running every 5th frame (500ms), we maintain 10 FPS
for the MJPEG stream while still updating YOLO detections twice per second.

**MJPEG stream (`/insect/stream`):**
This endpoint returns a multipart MIME stream of JPEG frames. The dashboard
embeds it with a plain `<img>` tag — the browser handles MJPEG natively.
This is the simplest possible video streaming solution with zero WebSocket
setup, zero ffmpeg, zero client-side decoding logic.

**Simulation mode:**
If no webcam is detected (cap.isOpened() returns False), the sidecar switches
to simulation mode: generates random XY coordinates. This allows the Spring
backend to start and function normally during development without insects.
During the actual demo, use real insects.

```python
"""
insect_sidecar/main.py
Flask server providing live insect detection as an entropy source.

Endpoints:
  GET /insect/reading   — Latest insect coordinates (JSON)
  GET /insect/stream    — MJPEG webcam feed (embed via <img> tag)
  GET /health           — Status check

Called by Spring's EntropyPool via HTTP every transaction.
Polled by EntropyController SSE loop every 500ms.
"""

import time
import threading
import json
import numpy as np
import cv2
from flask import Flask, jsonify, Response

app = Flask(__name__)

# ── Global state (updated by background thread) ────────────────
state = {
    "insects": [],
    "count": 0,
    "captured_at_ns": 0,
    "frame_id": "0",
    "simulation": False
}
latest_jpeg = None
state_lock = threading.Lock()

# ── Load YOLOv8 ────────────────────────────────────────────────
yolo_model = None
try:
    from ultralytics import YOLO
    yolo_model = YOLO("yolov8n.pt")
    print("YOLOv8n model loaded.")
except Exception as e:
    print(f"YOLOv8 unavailable ({e}). Using motion detection only.")


def webcam_loop():
    """Main capture loop. Runs in background thread."""
    global latest_jpeg

    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    cap.set(cv2.CAP_PROP_FPS, 10)

    if not cap.isOpened():
        print("No webcam detected. Starting simulation mode.")
        simulation_loop()
        return

    print("Webcam opened. Starting detection loop...")
    prev_gray = None
    frame_num = 0

    while True:
        ok, frame = cap.read()
        if not ok:
            time.sleep(0.05)
            continue

        frame_num += 1
        insects = []
        h, w = frame.shape[:2]

        # ── Motion detection ────────────────────────────────────
        gray = cv2.GaussianBlur(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY), (5, 5), 0)

        if prev_gray is not None:
            diff = cv2.absdiff(prev_gray, gray)
            _, thresh = cv2.threshold(diff, 20, 255, cv2.THRESH_BINARY)
            contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

            for c in contours:
                area = cv2.contourArea(c)
                if area < 8 or area > 40000:
                    continue
                M = cv2.moments(c)
                if M["m00"] == 0:
                    continue
                cx, cy = M["m10"] / M["m00"], M["m01"] / M["m00"]
                insects.append({
                    "x": round(cx / w, 6),
                    "y": round(cy / h, 6),
                    "confidence": round(min(area / 800, 1.0), 4)
                })
                x, y, bw, bh = cv2.boundingRect(c)
                cv2.rectangle(frame, (x, y), (x + bw, y + bh), (0, 230, 120), 1)
                cv2.putText(frame, f"{cx/w:.2f},{cy/h:.2f}",
                            (x, y - 3), cv2.FONT_HERSHEY_SIMPLEX, 0.3, (0, 230, 120), 1)

        # ── YOLOv8 (every 5th frame) ────────────────────────────
        if yolo_model is not None and frame_num % 5 == 0:
            try:
                results = yolo_model(frame, verbose=False, conf=0.25)
                for r in results:
                    for box in r.boxes:
                        x1, y1, x2, y2 = box.xyxy[0].tolist()
                        insects.append({
                            "x": round((x1 + x2) / 2 / w, 6),
                            "y": round((y1 + y2) / 2 / h, 6),
                            "confidence": round(float(box.conf[0]), 4)
                        })
                        cv2.rectangle(frame, (int(x1), int(y1)), (int(x2), int(y2)),
                                      (255, 140, 0), 2)
            except Exception:
                pass

        # ── HUD overlay ──────────────────────────────────────────
        cv2.rectangle(frame, (0, 0), (w, 32), (10, 14, 20), -1)
        cv2.putText(frame, f"NIDHI ENTROPY | Entities: {len(insects)} | Frame: {frame_num}",
                    (8, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 230, 120), 1)

        # ── Update shared state ───────────────────────────────────
        with state_lock:
            state.update({
                "insects": insects[:20],
                "count": len(insects),
                "captured_at_ns": time.time_ns(),
                "frame_id": str(frame_num),
                "simulation": False
            })
            _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 72])
            latest_jpeg = buf.tobytes()

        prev_gray = gray
        time.sleep(0.1)

    cap.release()


def simulation_loop():
    """Generates synthetic insect data when webcam is unavailable."""
    frame_num = 0
    while True:
        frame_num += 1
        n = np.random.randint(3, 9)
        insects = [{
            "x": round(float(np.random.random()), 6),
            "y": round(float(np.random.random()), 6),
            "confidence": round(float(np.random.uniform(0.5, 1.0)), 4)
        } for _ in range(n)]

        with state_lock:
            state.update({
                "insects": insects,
                "count": n,
                "captured_at_ns": time.time_ns(),
                "frame_id": f"sim-{frame_num}",
                "simulation": True
            })

        time.sleep(0.1)


# ── Flask routes ───────────────────────────────────────────────

@app.route("/insect/reading")
def reading():
    """
    Called by Spring's EntropyPool on every transaction.
    Returns current insect coordinates.
    Spring expects camelCase field names (InsectReading record).
    """
    with state_lock:
        s = dict(state)
    return jsonify({
        "insects": s["insects"],
        "count": s["count"],
        "capturedAtNs": s["captured_at_ns"],
        "frameId": s["frame_id"]
    })


@app.route("/insect/stream")
def stream():
    """
    MJPEG stream. Embed in dashboard HTML:
      <img src="http://localhost:5001/insect/stream">
    Browser handles MJPEG natively. No JavaScript needed.
    """
    def gen():
        while True:
            with state_lock:
                frame = latest_jpeg
            if frame:
                yield (b"--frame\r\n"
                       b"Content-Type: image/jpeg\r\n\r\n" + frame + b"\r\n")
            time.sleep(0.1)

    return Response(gen(), mimetype="multipart/x-mixed-replace; boundary=frame")


@app.route("/health")
def health():
    with state_lock:
        count = state["count"]
        sim = state["simulation"]
    return jsonify({
        "status": "UP",
        "insectCount": count,
        "webcamOnline": not sim,
        "simulationMode": sim
    })


# ── Start ──────────────────────────────────────────────────────

if __name__ == "__main__":
    t = threading.Thread(target=webcam_loop, daemon=True)
    t.start()
    time.sleep(1.0)  # Let first frame capture
    print("Insect entropy sidecar ready.")
    print("  /insect/reading  — coordinate API")
    print("  /insect/stream   — MJPEG feed")
    print("  /health          — status")
    app.run(host="0.0.0.0", port=5001, debug=False, threaded=True)
```

---

## SECTION 5: DASHBOARD HTML

### 5.1 src/main/resources/static/dashboard.html

[CREATE FILE: `nidhi-backend/src/main/resources/static/dashboard.html`]

[EXPLAIN — DASHBOARD LAYOUT]

Four panels:
1. **Top-left — Entropy Pool:** Five source cards (green=active, red=offline).
   Shows last generated seed hex. Connects via SSE to `/entropy/live`.
2. **Top-right — Live Feed:** MJPEG stream from Python sidecar via `<img>` tag.
   Live coordinate list updates from SSE data.
3. **Right column (full height) — Audit Log:** Real-time Merkle-chained events.
   Polled from `/dashboard/audit` every 2 seconds.
4. **Bottom strip — Stats:** Transaction count, insect count, avg response time,
   active source count, system status.

All data updates happen via:
- SSE subscription to `/entropy/live` for insect + source data
- `setInterval` polling of `/dashboard/audit` every 2000ms

No frameworks. No build step. No dependencies. Pure HTML/CSS/JS.
Spring serves this as a static file. Access at `http://localhost:8080/dashboard.html`.

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Nidhi Ops Dashboard</title>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@300;400;600&family=Syne:wght@700;800&display=swap" rel="stylesheet">
<style>
:root {
  --bg:#07090d; --surface:#0d1018; --card:#111520; --border:#1a2030;
  --green:#00e5a0; --red:#ff4d6d; --amber:#ffc947; --purple:#a78bfa;
  --text:#dde2ee; --muted:#3d4a60;
}
*{margin:0;padding:0;box-sizing:border-box;}
body{background:var(--bg);color:var(--text);font-family:'IBM Plex Mono',monospace;min-height:100vh;}
body::before{content:'';position:fixed;inset:0;
  background-image:linear-gradient(rgba(0,229,160,.02) 1px,transparent 1px),
  linear-gradient(90deg,rgba(0,229,160,.02) 1px,transparent 1px);
  background-size:36px 36px;pointer-events:none;z-index:0;}
.wrap{position:relative;z-index:1;}

/* HEADER */
.hdr{display:flex;align-items:center;justify-content:space-between;
  padding:14px 24px;border-bottom:1px solid var(--border);
  background:rgba(7,9,13,.96);position:sticky;top:0;z-index:100;}
.logo{font-family:'Syne',sans-serif;font-size:18px;font-weight:800;color:var(--green);}
.logo span{color:var(--text);}
.pills{display:flex;gap:8px;}
.pill{padding:4px 10px;border-radius:2px;border:1px solid;font-size:10px;
  font-weight:600;letter-spacing:.1em;display:flex;align-items:center;gap:5px;}
.pill.ok{border-color:var(--green);color:var(--green);background:rgba(0,229,160,.07);}
.pill.warn{border-color:var(--amber);color:var(--amber);background:rgba(255,201,71,.07);}
.pill.err{border-color:var(--red);color:var(--red);background:rgba(255,77,109,.07);}
.dot{width:6px;height:6px;border-radius:50%;background:currentColor;
  animation:blink 1.4s ease-in-out infinite;}
@keyframes blink{0%,100%{opacity:1;}50%{opacity:.25;}}
.clk{font-size:11px;color:var(--muted);}

/* GRID */
.grid{display:grid;grid-template-columns:1fr 1fr 360px;
  grid-template-rows:auto 1fr;gap:2px;padding:2px;min-height:calc(100vh - 53px);}

/* PANELS */
.panel{background:var(--card);border:1px solid var(--border);
  display:flex;flex-direction:column;overflow:hidden;}
.ph{padding:12px 16px;border-bottom:1px solid var(--border);background:var(--surface);
  display:flex;align-items:center;justify-content:space-between;}
.pt{font-family:'Syne',sans-serif;font-size:11px;font-weight:700;
  letter-spacing:.15em;color:var(--green);text-transform:uppercase;}
.pm{font-size:10px;color:var(--muted);}
.pb{padding:16px;flex:1;overflow-y:auto;}

/* SOURCE CARDS */
.src-grid{display:grid;grid-template-columns:1fr 1fr;gap:2px;margin-bottom:14px;}
.src{background:var(--surface);border:1px solid var(--border);padding:10px 12px;transition:border-color .3s;}
.src.on{border-color:rgba(0,229,160,.35);}
.src.off{border-color:rgba(255,77,109,.35);opacity:.55;}
.sn{font-size:9px;font-weight:600;letter-spacing:.15em;color:var(--muted);text-transform:uppercase;margin-bottom:3px;}
.sv{font-size:12px;font-weight:600;color:var(--green);}
.src.off .sv{color:var(--red);}
.seed-lbl{font-size:9px;color:var(--muted);letter-spacing:.2em;text-transform:uppercase;margin-bottom:6px;}
.seed-val{background:#05070a;border:1px solid var(--border);border-left:3px solid var(--green);
  padding:10px 12px;font-size:10px;line-height:1.9;word-break:break-all;color:var(--green);}

/* FEED */
.feed-img{width:100%;aspect-ratio:4/3;background:#000;border:1px solid var(--border);display:block;object-fit:cover;}
.coords{margin-top:10px;max-height:110px;overflow-y:auto;}
.cr{display:flex;gap:12px;padding:3px 0;border-bottom:1px solid var(--border);
  font-size:10px;animation:fi .3s ease;}
@keyframes fi{from{background:rgba(0,229,160,.12);}to{background:transparent;}}
.ci{color:var(--muted);width:18px;}.cx{color:var(--green);flex:1;}.cc{color:var(--amber);}

/* AUDIT */
.ae{padding:9px 0;border-bottom:1px solid var(--border);animation:si .4s ease;}
@keyframes si{from{opacity:0;transform:translateX(8px);}to{opacity:1;transform:none;}}
.at{font-size:9px;font-weight:600;letter-spacing:.12em;margin-bottom:3px;}
.at.CHALLENGE_ISSUED{color:var(--amber);}
.at.CHALLENGE_VERIFIED{color:var(--green);}
.at.CHALLENGE_FAILED{color:var(--red);}
.at.TRANSACTION_COMMITTED{color:var(--purple);}
.ad{font-size:10px;color:#5a6880;line-height:1.6;margin-bottom:3px;}
.ah{font-size:8px;color:var(--muted);display:flex;gap:6px;flex-wrap:wrap;}
.hc{background:var(--surface);border:1px solid var(--border);padding:1px 5px;}

/* STATS */
#stats{grid-column:1/3;grid-row:2;}
.srow{display:flex;gap:2px;height:100%;}
.sb{flex:1;background:var(--surface);border:1px solid var(--border);
  padding:16px 20px;display:flex;flex-direction:column;justify-content:center;}
.sl{font-size:9px;color:var(--muted);letter-spacing:.2em;text-transform:uppercase;margin-bottom:6px;}
.sv2{font-family:'Syne',sans-serif;font-size:28px;font-weight:800;color:var(--green);line-height:1;}
.ss{font-size:10px;color:var(--muted);margin-top:5px;}

::-webkit-scrollbar{width:3px;}
::-webkit-scrollbar-track{background:var(--bg);}
::-webkit-scrollbar-thumb{background:var(--border);}
</style>
</head>
<body>
<div class="wrap">

<div class="hdr">
  <div style="display:flex;align-items:center;gap:14px">
    <div class="logo">Nidhi <span>Ops</span></div>
    <div class="pills">
      <div class="pill ok" id="p-backend"><div class="dot"></div>BACKEND</div>
      <div class="pill warn" id="p-insect"><div class="dot"></div>INSECTS:&nbsp;<span id="ic-hdr">—</span></div>
    </div>
  </div>
  <div class="clk" id="clk">--:--:--</div>
</div>

<div class="grid">

  <!-- ENTROPY PANEL -->
  <div class="panel">
    <div class="ph"><div class="pt">Entropy Pool</div><div class="pm" id="eu-time">—</div></div>
    <div class="pb">
      <div class="src-grid">
        <div class="src on" id="s-TRNG"><div class="sn">S1 · TRNG</div><div class="sv">ACTIVE</div></div>
        <div class="src on" id="s-INSECT"><div class="sn">S2 · Biological</div><div class="sv" id="ins-sv">ACTIVE</div></div>
        <div class="src on" id="s-THERMAL"><div class="sn">S3 · Thermal</div><div class="sv">ACTIVE</div></div>
        <div class="src on" id="s-JITTER"><div class="sn">S4 · Net Jitter</div><div class="sv">ACTIVE</div></div>
        <div class="src on" id="s-CLIENT" style="grid-column:span 2"><div class="sn">S5 · Client Nonce</div><div class="sv">ACTIVE</div></div>
      </div>
      <div class="seed-lbl">// Last XOR → SHA-256 Seed</div>
      <div class="seed-val" id="seed-val">Waiting for first transaction...</div>
    </div>
  </div>

  <!-- LIVE FEED PANEL -->
  <div class="panel">
    <div class="ph"><div class="pt">Live Entropy Feed</div><div class="pm">YOLOv8 + motion detection</div></div>
    <div class="pb">
      <img class="feed-img" src="http://localhost:5001/insect/stream" id="feed"
           onerror="this.alt='Start insect_sidecar/main.py to see live feed'">
      <div class="coords" id="clist"><div style="color:var(--muted);font-size:10px;padding:6px 0">Waiting for coordinate data...</div></div>
    </div>
  </div>

  <!-- AUDIT LOG -->
  <div class="panel" style="grid-row:1/3">
    <div class="ph"><div class="pt">Audit Log</div><div class="pm">Merkle-chained · <span id="te">0</span> entries</div></div>
    <div class="pb" id="alist"><div style="color:var(--muted);font-size:10px">No transactions yet.</div></div>
  </div>

  <!-- STATS -->
  <div class="panel" id="stats">
    <div class="pb" style="padding:0">
      <div class="srow">
        <div class="sb"><div class="sl">Total Transactions</div><div class="sv2" id="s-total">0</div><div class="ss">this session</div></div>
        <div class="sb"><div class="sl">Live Entities</div><div class="sv2" id="s-ins" style="color:var(--green)">—</div><div class="ss">in entropy chamber</div></div>
        <div class="sb"><div class="sl">Avg Response</div><div class="sv2" id="s-avg" style="color:var(--amber)">—</div><div class="ss">ms (challenge → verify)</div></div>
        <div class="sb"><div class="sl">Active Sources</div><div class="sv2" id="s-srcs" style="color:var(--purple)">5</div><div class="ss">of 5 entropy sources</div></div>
        <div class="sb" style="flex:2">
          <div class="sl">System Status</div>
          <div id="sys-status" style="font-size:13px;color:var(--green);font-weight:600;margin-top:4px">ALL SYSTEMS OPERATIONAL</div>
          <div class="ss" id="sys-sub">Biological entropy active · Challenge engine ready</div>
        </div>
      </div>
    </div>
  </div>

</div>
</div>

<script>
// ── State ──────────────────────────────────────────────────────
let lastAuditCount = 0, totalTx = 0;

// ── Clock ──────────────────────────────────────────────────────
setInterval(() => {
  document.getElementById('clk').textContent =
    new Date().toLocaleTimeString('en-IN', {hour12: false});
}, 1000);

// ── SSE: Entropy live stream ───────────────────────────────────
const es = new EventSource('/entropy/live');

es.addEventListener('entropy-update', e => {
  const d = JSON.parse(e.data);

  // Update insect count displays
  const ic = d.insectCount || 0;
  document.getElementById('ic-hdr').textContent = ic;
  document.getElementById('s-ins').textContent = ic;
  document.getElementById('p-insect').className = 'pill ' + (ic >= 2 ? 'ok' : 'warn');

  // Source cards
  const srcs = d.sourcesActive || [];
  document.getElementById('s-srcs').textContent = srcs.length;

  const insectOn = srcs.includes('INSECT');
  const insectCard = document.getElementById('s-INSECT');
  insectCard.className = 'src ' + (insectOn ? 'on' : 'off');
  document.getElementById('ins-sv').textContent = insectOn ? 'ACTIVE' : 'FALLBACK';

  document.getElementById('eu-time').textContent =
    'updated ' + new Date().toLocaleTimeString('en-IN', {hour12: false});

  // Coordinate list
  const insects = d.insects || [];
  if (insects.length > 0) {
    document.getElementById('clist').innerHTML = insects.slice(0, 8).map((ins, i) =>
      `<div class="cr">
        <span class="ci">${i+1}</span>
        <span class="cx">x:${ins.x.toFixed(4)} y:${ins.y.toFixed(4)}</span>
        <span class="cc">${(ins.confidence*100).toFixed(0)}%</span>
       </div>`
    ).join('');
  }
});

es.onerror = () => {
  document.getElementById('p-backend').className = 'pill err';
  document.getElementById('sys-status').textContent = 'BACKEND DISCONNECTED';
  document.getElementById('sys-status').style.color = 'var(--red)';
};

// ── Poll: Audit log ────────────────────────────────────────────
async function pollAudit() {
  try {
    const r = await fetch('/dashboard/audit');
    if (!r.ok) throw new Error('bad response');
    const d = await r.json();

    document.getElementById('p-backend').className = 'pill ok';
    document.getElementById('te').textContent = d.totalEntries;

    const entries = d.entries || [];

    // Count committed
    const committed = entries.filter(e => e.type === 'TRANSACTION_COMMITTED').length;
    if (committed !== totalTx) {
      totalTx = committed;
      document.getElementById('s-total').textContent = committed;
    }

    // Avg response time
    const verified = entries.filter(e => e.type === 'CHALLENGE_VERIFIED');
    if (verified.length > 0) {
      const times = verified.map(e => {
        const m = e.details.match(/(\d+)ms/);
        return m ? parseInt(m[1]) : null;
      }).filter(Boolean);
      if (times.length > 0) {
        document.getElementById('s-avg').textContent =
          Math.round(times.reduce((a,b) => a+b, 0) / times.length);
      }
    }

    // Render audit entries
    if (entries.length !== lastAuditCount) {
      lastAuditCount = entries.length;
      renderAudit(entries);
    }

    // Update seed display
    const issued = entries.find(e => e.type === 'CHALLENGE_ISSUED');
    if (issued) {
      const m = issued.details.match(/Seed: ([a-f0-9]+)/);
      if (m) document.getElementById('seed-val').textContent = m[1] + '...';
    }

  } catch (e) {
    document.getElementById('p-backend').className = 'pill err';
  }
}

function renderAudit(entries) {
  const el = document.getElementById('alist');
  if (!entries.length) {
    el.innerHTML = '<div style="color:var(--muted);font-size:10px">No transactions yet.</div>';
    return;
  }
  el.innerHTML = entries.map(e => `
    <div class="ae">
      <div class="at ${e.type}">${e.type.replace(/_/g,' ')}</div>
      <div class="ad">${e.details}</div>
      <div class="ah">
        <span class="hc">#${e.sequence}</span>
        <span class="hc">${e.entryHash.substring(0,10)}...</span>
        <span style="font-size:8px;color:var(--muted)">${e.timestamp.substring(11,19)}</span>
      </div>
    </div>`).join('');
}

pollAudit();
setInterval(pollAudit, 2000);
</script>
</body>
</html>
```

---

## SECTION 6: VERIFICATION GATES

After writing all files, the agent must execute these verifications in order.
Failure at any gate requires diagnosis and fix before proceeding.

### Gate 1: File Completeness

[EXECUTE]
```bash
echo "=== Java files ===" && find src/main/java -name "*.java" | sort
echo "=== Resources ===" && find src/main/resources -type f | sort
echo "=== Python ===" && find insect_sidecar -type f | sort
echo "=== pom.xml ===" && test -f pom.xml && echo "EXISTS" || echo "MISSING"
```

[VERIFY] Expected output includes exactly these files:
```
src/main/java/com/nidhi/NidhiApplication.java
src/main/java/com/nidhi/challenge/ChallengeEngine.java
src/main/java/com/nidhi/common/CorsConfig.java
src/main/java/com/nidhi/common/WebClientConfig.java
src/main/java/com/nidhi/dashboard/AuditLog.java
src/main/java/com/nidhi/dashboard/DashboardController.java
src/main/java/com/nidhi/entropy/EntropyController.java
src/main/java/com/nidhi/entropy/EntropyPool.java
src/main/java/com/nidhi/transaction/TransactionController.java
src/main/java/com/nidhi/transaction/TransactionService.java
src/main/java/com/nidhi/voice/BhashiniClient.java
src/main/java/com/nidhi/voice/IntentParser.java
src/main/java/com/nidhi/voice/VoiceController.java
src/main/java/com/nidhi/voice/VoiceService.java
src/main/resources/application.properties
src/main/resources/static/dashboard.html
insect_sidecar/main.py
insect_sidecar/requirements.txt
pom.xml
```

### Gate 2: Maven Compilation

[EXECUTE]
```bash
mvn clean compile -q 2>&1
echo "Exit code: $?"
```

[VERIFY] Exit code must be 0. Any non-zero exit requires reading the error output
and fixing the Java syntax error before continuing.

Common errors and fixes:
- `cannot find symbol: HexFormat` → Java version < 17. Check `java -version`.
- `cannot find symbol: @Qualifier` → Missing import. Add `import org.springframework.beans.factory.annotation.Qualifier;`
- `record ... cannot extend` → Records cannot extend classes. Check record declarations.
- `package ... does not exist` → Wrong package name or wrong directory. Verify file path matches package declaration.

### Gate 3: Python Sidecar Smoke Test

[EXECUTE]
```bash
cd insect_sidecar
python3 main.py &
sleep 3
curl -s http://localhost:5001/health | python3 -m json.tool
curl -s http://localhost:5001/insect/reading | python3 -m json.tool
kill %1
cd ..
```

[VERIFY] `/health` response must contain `"status": "UP"`.
[VERIFY] `/insect/reading` response must contain `insects`, `count`, `capturedAtNs`, `frameId` fields.

### Gate 4: Spring Boot Startup

[EXECUTE]
```bash
# Start sidecar in background
cd insect_sidecar && python3 main.py &
SIDECAR_PID=$!
cd ..

# Start Spring Boot
mvn spring-boot:run &
SPRING_PID=$!

# Wait for startup
sleep 12

# Health check
curl -s http://localhost:8080/health | python3 -m json.tool

kill $SIDECAR_PID $SPRING_PID 2>/dev/null
```

[VERIFY] Health response must contain `"status": "UP"`.

### Gate 5: End-to-End Transaction Test

[EXECUTE — run with sidecar + Spring both started]
```bash
# Test voice parse
curl -s -X POST http://localhost:8080/voice/parse \
  -H "Content-Type: application/json" \
  -d '{"audioBase64":null,"textFallback":"send 500 rupees to Ramu","languageCode":"en"}' \
  | python3 -m json.tool

# Test transaction initiate
INITIATE=$(curl -s -X POST http://localhost:8080/transaction/initiate \
  -H "Content-Type: application/json" \
  -d '{"audioBase64":null,"textFallback":"send 500 rupees to Ramu","languageCode":"en","deviceId":"test-device-001","clientNonceHex":null}')

echo "$INITIATE" | python3 -m json.tool

# Extract txId and signingPayload
TX_ID=$(echo "$INITIATE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('txId',''))")
SIGN_PAYLOAD=$(echo "$INITIATE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('signingPayload',''))")
PRIV_KEY=$(echo "$INITIATE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('privateKeyBase64',''))")

echo "txId: $TX_ID"
echo "signingPayload: $SIGN_PAYLOAD"

# Sign and confirm using Python (simulates what mobile app does)
SIGNATURE=$(python3 -c "
import base64, sys
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.backends import default_backend

priv_bytes = base64.b64decode('$PRIV_KEY')
priv_key = serialization.load_der_private_key(priv_bytes, password=None, backend=default_backend())
sig = priv_key.sign('$SIGN_PAYLOAD'.encode(), ec.ECDSA(hashes.SHA256()))
print(base64.b64encode(sig).decode())
" 2>/dev/null || echo "CRYPTO_LIB_MISSING")

if [ "$SIGNATURE" = "CRYPTO_LIB_MISSING" ]; then
  echo "cryptography library not installed. Install: pip3 install cryptography"
  echo "Skipping confirm test. Manual confirmation required."
else
  curl -s -X POST http://localhost:8080/transaction/confirm \
    -H "Content-Type: application/json" \
    -d "{\"txId\":\"$TX_ID\",\"deviceId\":\"test-device-001\",\"signatureBase64\":\"$SIGNATURE\"}" \
    | python3 -m json.tool
fi
```

[VERIFY]
- `/voice/parse` response: `success: true`, `recipient: "Ramu"`, `amountPaise: 50000`
- `/transaction/initiate` response: `success: true`, `txId` is a UUID, `signingPayload` is non-empty
- `/transaction/confirm` response (if crypto available): `success: true`, `referenceNumber` matches `NID-*`
- Dashboard at `http://localhost:8080/dashboard.html` renders and shows audit entries

---

## SECTION 7: API CONTRACT REFERENCE

This section is the official contract for the mobile app teammate.
Share this on Hour 0.

### Endpoints

| Method | Path | Called By | Purpose |
|--------|------|-----------|---------|
| POST | /voice/parse | Mobile App | Parse audio/text → structured intent + TTS confirmation |
| POST | /transaction/initiate | Mobile App | Get challenge for a transaction |
| POST | /transaction/confirm | Mobile App | Submit signed response, get reference number |
| GET | /entropy/live | Dashboard (SSE) | Real-time insect coordinate stream |
| GET | /entropy/status | Dashboard | Entropy source health snapshot |
| GET | /dashboard/audit | Dashboard | Last 20 audit log entries (polled) |
| GET | /health | Mobile App | Liveness probe |

### POST /voice/parse

Request:
```json
{
  "audioBase64": "UklGRiQ...",
  "textFallback": "send 500 rupees to Ramu",
  "languageCode": "hi"
}
```

Response (success):
```json
{
  "success": true,
  "originalText": "रामू को पाँच सौ रुपये भेजो",
  "translatedText": "send five hundred rupees to Ramu",
  "intentType": "TRANSFER",
  "recipient": "Ramu",
  "amountPaise": 50000,
  "formattedAmount": "Rs 500",
  "confirmationText": "रामू को पाँच सौ रुपये भेजना है?",
  "language": "hi",
  "confidence": 0.85,
  "usedAudio": true,
  "errorMessage": null
}
```

### POST /transaction/initiate

Request:
```json
{
  "audioBase64": null,
  "textFallback": "send 500 rupees to Ramu",
  "languageCode": "en",
  "deviceId": "device-abc-123",
  "clientNonceHex": null
}
```

Response (success):
```json
{
  "success": true,
  "txId": "550e8400-e29b-41d4-a716-446655440000",
  "recipient": "Ramu",
  "amountPaise": 50000,
  "formattedAmount": "Rs 500",
  "confirmationText": "Send 500 rupees to Ramu?",
  "language": "en",
  "signingPayload": "a1b2c3d4...|e5f6a7b8...|1",
  "expiresAtMs": 1700000005000,
  "privateKeyBase64": "MIGHAgEA...",
  "sourcesActive": ["TRNG","INSECT","THERMAL","JITTER","CLIENT"],
  "errorMessage": null
}
```

### POST /transaction/confirm

Request:
```json
{
  "txId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceId": "device-abc-123",
  "signatureBase64": "MEYCIQDxyz..."
}
```

Response (success):
```json
{
  "success": true,
  "txId": "550e8400-e29b-41d4-a716-446655440000",
  "referenceNumber": "NID-42891",
  "recipient": "Ramu",
  "amountPaise": 50000,
  "formattedAmount": "Rs 500",
  "seedHex": "a1b2c3d4...",
  "sourcesActive": ["TRNG","INSECT","THERMAL","JITTER","CLIENT"],
  "responseTimeMs": 234,
  "errorCode": null,
  "errorMessage": null
}
```

### Signing Code for Mobile App

Android Java:
```java
byte[] privateKeyBytes = Base64.decode(initiateResponse.privateKeyBase64, Base64.DEFAULT);
PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(spec);

Signature sig = Signature.getInstance("SHA256withECDSA");
sig.initSign(privateKey);
sig.update(initiateResponse.signingPayload.getBytes(StandardCharsets.UTF_8));
String signatureBase64 = Base64.encodeToString(sig.sign(), Base64.NO_WRAP);
```

Android Kotlin:
```kotlin
val privateKeyBytes = Base64.decode(initiateResponse.privateKeyBase64, Base64.DEFAULT)
val privateKey = KeyFactory.getInstance("EC")
    .generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
val sig = Signature.getInstance("SHA256withECDSA").apply {
    initSign(privateKey)
    update(initiateResponse.signingPayload.toByteArray(Charsets.UTF_8))
}
val signatureBase64 = Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
```

---

## SECTION 8: DEMO SCRIPT

This is the exact sequence to execute during the 3-minute demo presentation.

### Setup (before judges arrive)

1. Start sidecar: `cd insect_sidecar && python3 main.py`
2. Start Spring Boot: `mvn spring-boot:run`
3. Open dashboard: `http://localhost:8080/dashboard.html` on second monitor
4. Verify insect count > 0 in dashboard header
5. Run one test transaction to pre-warm the JVM and confirm end-to-end works
6. Place the insect container under the webcam — confirm bounding boxes visible

### During Demo

**Minute 0-1: Setup the story**
> "There are 250 million Indians who can't read a PIN, live where 4G is a rumour,
> and have never had a bank account. Nidhi lets them send money using only their voice,
> in their own language, secured by something no algorithm can predict: living creatures."

Point to the insect feed on the second monitor.

**Minute 1-2: Live demo**

1. Teammate shows the phone. User speaks: "Ramu ko pachaas rupaye bhejo" (Hindi)
2. App plays back: "रामू को पचास रुपये भेजना है?" — user taps confirm
3. Dashboard shows: CHALLENGE_ISSUED → CHALLENGE_VERIFIED → TRANSACTION_COMMITTED
4. Phone shows: ✓ Approved — NID-XXXXX
5. Point to the insect feed: "Those coordinates just secured that transaction"

**Minute 2-3: Q&A buffer**

Common questions — see Battle Plan Section 6 for rehearsed answers.

### Demo Failure Recovery

If Bhashini is slow/down:
→ Use text fallback: type "send 500 to Ramu" in the app's text field
→ Say: "Voice ASR is calling Bhashini API — for speed, I'm using text input now.
         In production, we'd add local on-device ASR as a fallback."

If insect sidecar is down:
→ Dashboard shows INSECT_FALLBACK in entropy sources
→ Say: "The biological source is the showpiece, but notice the system is still
         running securely on the other four sources. Graceful degradation by design."

If Spring Boot crashes:
→ Restart: `mvn spring-boot:run` (takes ~8 seconds)
→ Have the command pre-typed in a terminal

---

## SECTION 9: KNOWN LIMITATIONS AND HONEST STATEMENTS

Be proactive about these in Q&A. Judges respect honesty.

| Limitation | Honest Statement |
|---|---|
| Private key sent in response | "In production this runs in the hardware TEE. The TEE generates the keypair and the private key never leaves the chip. Today it's a software simulation of that behavior." |
| No persistent storage | "We use in-memory storage for the hackathon. In production, challenges and audit log entries would be written to a tamper-evident database." |
| Mock ledger | "This is where we'd call the payment rail — IMPS, UPI, or a banking core API. The reference number is generated; in production it comes back from the bank." |
| Insect count variance | "The entropy system works with any insect count ≥ 2. With zero insects we fall back to four other sources, all cryptographically sound." |
| Bhashini latency | "Bhashini is a free government API and can be slow. Production would use a private deployment or on-device ASR as primary, Bhashini as backup." |

---

END OF SPECIFICATION
Total files to create: 19
Total lines of code: ~1,800
Estimated build time with this spec: 2-3 hours for a competent agent
