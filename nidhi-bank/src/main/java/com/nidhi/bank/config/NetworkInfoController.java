package com.nidhi.bank.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes the server's local network IP so the admin dashboard can display
 * it and generate a QR code for the Android app to scan.
 */
@Slf4j
@RestController
public class NetworkInfoController {

    @Value("${server.port:8082}")
    private int bankPort;

    @Value("${backend.port:8081}")
    private int backendPort;

    @GetMapping("/api/server-info")
    public Map<String, Object> serverInfo() {
        String ip = detectLocalIp();
        String backendUrl = "http://" + ip + ":" + backendPort;
        String bankUrl    = "http://" + ip + ":" + bankPort;

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("localIp",     ip);
        info.put("backendPort", backendPort);
        info.put("bankPort",    bankPort);
        info.put("backendUrl",  backendUrl);
        info.put("bankUrl",     bankUrl);
        info.put("adminUrl",    bankUrl + "/admin.html");
        // Hint for Windows Mobile Hotspot (default gateway IP)
        info.put("hotspotIp",   "192.168.137.1");
        return info;
    }

    /**
     * Walk all network interfaces and return the first non-loopback IPv4 address,
     * preferring hotspot (192.168.137.x) or WiFi (192.168.x.x) over others.
     */
    private String detectLocalIp() {
        String fallback = "127.0.0.1";
        String wifi     = null;
        String hotspot  = null;

        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;

                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress() || !(addr instanceof Inet4Address)) continue;

                    String ip = addr.getHostAddress();
                    if (ip.startsWith("192.168.137.")) {
                        hotspot = ip;  // Windows Mobile Hotspot adapter
                    } else if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                        if (wifi == null) wifi = ip;
                    } else if (fallback.equals("127.0.0.1")) {
                        fallback = ip;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not detect local IP: {}", e.getMessage());
        }

        // Prefer hotspot > WiFi > other > loopback
        if (hotspot != null) return hotspot;
        if (wifi    != null) return wifi;
        return fallback;
    }
}
