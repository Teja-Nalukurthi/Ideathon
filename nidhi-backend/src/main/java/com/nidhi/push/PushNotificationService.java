package com.nidhi.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages one SSE sink per connected account.
 * When the Android app is open it holds a live HTTP connection here;
 * when a transfer completes TransactionService calls push() which
 * instantly delivers the notification over that connection.
 */
@Slf4j
@Service
public class PushNotificationService {

    // account-number → sink for that device's live connection
    private final ConcurrentMap<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    /**
     * Called by the SSE controller to create / attach to a sink.
     * Returns a Flux that streams push events plus a heartbeat comment
     * every 25 s to prevent proxy / NAT timeouts.
     */
    public Flux<ServerSentEvent<String>> subscribe(String account) {
        Sinks.Many<String> sink = Sinks.many().multicast().directBestEffort();
        sinks.put(account, sink);
        log.info("SSE subscription opened for account={}", account);

        Flux<ServerSentEvent<String>> events = sink.asFlux()
                .map(msg -> ServerSentEvent.<String>builder()
                        .event("push")
                        .data(msg)
                        .build())
                .doFinally(signal -> {
                    sinks.remove(account, sink);
                    log.info("SSE subscription closed for account={}", account);
                });

        // heartbeat — keeps the connection alive through firewalls / Android Doze
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(i -> ServerSentEvent.<String>builder().comment("heartbeat").build());

        return Flux.merge(events, heartbeat);
    }

    /**
     * Push a plain-text notification message to a connected account.
     * Safe to call even if no client is connected (no-op).
     */
    public void push(String account, String message) {
        Sinks.Many<String> sink = sinks.get(account);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(message);
            log.info("SSE push to account={} result={}", account, result);
        } else {
            log.debug("No SSE subscriber for account={} — notification skipped", account);
        }
    }

    /** True when an app instance is actively subscribed. Useful for logging. */
    public boolean isConnected(String account) {
        return sinks.containsKey(account);
    }
}
