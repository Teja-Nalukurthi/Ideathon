package com.nidhi.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class NotificationStreamController {

    private final PushNotificationService pushService;

    /**
     * GET /notifications/stream?account=NIDHI...
     *
     * The Android app opens this endpoint and keeps the connection alive.
     * The server streams ServerSentEvents (push events + heartbeats) over it.
     * Spring WebFlux / Reactor handle the async without blocking any thread.
     */
    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam String account) {
        log.info("New SSE connection from account={}", account);
        return pushService.subscribe(account);
    }

    /**
     * POST /notifications/test?account=NIDHI...
     * Admin/debug helper — fire a test notification to a connected device.
     */
    @PostMapping("/notifications/test")
    public Map<String, Object> testPush(@RequestParam String account,
                                         @RequestParam(defaultValue = "Test notification from Nidhi Bank") String message) {
        boolean connected = pushService.isConnected(account);
        if (connected) {
            pushService.push(account, message);
        }
        return Map.of(
                "connected", connected,
                "account", account,
                "message", connected ? "Notification sent" : "No active SSE connection for this account"
        );
    }
}
