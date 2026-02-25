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
public class EntropyController {

    private final EntropyPool entropyPool;
    private final WebClient sidecarClient;

    private final CopyOnWriteArrayList<SseEmitter> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public EntropyController(EntropyPool entropyPool,
                             @Qualifier("sidecarWebClient") WebClient sidecarClient) {
        this.entropyPool = entropyPool;
        this.sidecarClient = sidecarClient;
    }

    @PostConstruct
    public void startSseLoop() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                var reading = sidecarClient.get()
                        .uri("/insect/reading")
                        .retrieve()
                        .bodyToMono(EntropyPool.InsectReading.class)
                        .timeout(Duration.ofMillis(400))
                        .block();
                if (reading != null) entropyPool.updateInsectReading(reading);
                else entropyPool.clearInsectReading(); // null response = sidecar offline
            } catch (Exception e) {
                entropyPool.clearInsectReading(); // request failed = sidecar offline
            }

            int liveCount = entropyPool.getCurrentInsectCount();
            boolean insectActive = liveCount >= entropyPool.getMinInsectCount();
            String[] liveSources = insectActive
                    ? new String[]{"TRNG", "INSECT", "THERMAL", "JITTER", "CLIENT"}
                    : new String[]{"TRNG", "INSECT_FALLBACK", "THERMAL", "JITTER", "CLIENT"};

            // Keep pool in sync so /entropy/status and /dashboard/audit reflect live state
            entropyPool.setLastActiveSources(liveSources);

            if (clients.isEmpty()) return;

            var payload = Map.of(
                    "insectCount", liveCount,
                    "sourcesActive", liveSources,
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
                    return true;
                }
            });
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

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

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "insectCount", entropyPool.getCurrentInsectCount(),
                "sourcesActive", entropyPool.getLastActiveSources(),
                "insectOnline", entropyPool.getCurrentInsectCount() >= entropyPool.getMinInsectCount(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "nidhi-backend"));
    }
}
