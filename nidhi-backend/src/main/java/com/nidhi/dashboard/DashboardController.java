package com.nidhi.dashboard;

import com.nidhi.entropy.EntropyPool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final AuditLog auditLog;
    private final EntropyPool entropyPool;

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
