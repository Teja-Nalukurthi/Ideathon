package com.nidhi.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Map;

/**
 * Provides a connectivity health check and local network info for the Android app.
 * GET /api/health       — simple 200 OK, used by app to confirm reachability
 * GET /api/server-info  — full network details (mirrors nidhi-bank's endpoint)
 */
@RestController
public class NetworkInfoController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "nidhi-backend");
    }

    @GetMapping("/api/server-info")
    public Map<String, Object> serverInfo() {
        String localIp = detectLocalIp();
        int backendPort = 8081;
        int bankPort    = 8082;
        return Map.of(
                "localIp",    localIp,
                "backendPort", backendPort,
                "bankPort",    bankPort,
                "backendUrl",  "http://" + localIp + ":" + backendPort,
                "bankUrl",     "http://" + localIp + ":" + bankPort,
                "adminUrl",    "http://" + localIp + ":" + bankPort + "/admin.html",
                "hotspotIp",  "192.168.137.1"
        );
    }

    private String detectLocalIp() {
        // Prefer hotspot IP (192.168.137.x) for demo, else first non-loopback IPv4
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.137.")) return ip; // hotspot preferred
                    }
                }
            }
            // Fallback: any non-loopback IPv4
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
