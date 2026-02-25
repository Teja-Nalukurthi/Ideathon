package com.nidhi.bank.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/")
    public RedirectView root() {
        return new RedirectView("/admin.html");
    }

    /** GET /admin/users — list all users for admin dashboard */
    @GetMapping("/admin/users")
    public ResponseEntity<List<BankUser>> listUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /** GET /admin/stats — dashboard header stats */
    @GetMapping("/admin/stats")
    public ResponseEntity<UserService.StatsResponse> stats() {
        return ResponseEntity.ok(userService.getStats());
    }

    /** POST /admin/users — register a new user (admin only, local network) */
    @PostMapping("/admin/users")
    public ResponseEntity<?> registerUser(@RequestBody UserService.RegisterRequest req) {
        try {
            BankUser user = userService.registerUser(req);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** DELETE /admin/users/{id} — deactivate user */
    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(Map.of("status", "deactivated"));
    }

    /** PUT /admin/users/{id}/balance — adjust balance (admin seed tool) */
    @PutMapping("/admin/users/{id}/balance")
    public ResponseEntity<?> adjustBalance(@PathVariable Long id,
                                           @RequestBody Map<String, Long> body) {
        return userService.getByAccountNumber("") // find by id instead
                .map(u -> {
                    userService.updateBalance(u.getAccountNumber(), body.get("balancePaise"));
                    return ResponseEntity.ok(u);
                })
                .orElseGet(() -> userService.getAllUsers().stream()
                        .filter(u -> u.getId().equals(id))
                        .findFirst()
                        .map(u -> {
                            BankUser updated = userService.updateBalance(
                                    u.getAccountNumber(), body.get("balancePaise"));
                            return ResponseEntity.ok(updated);
                        })
                        .orElse(ResponseEntity.notFound().build()));
    }

    /**
     * POST /bank/device/register — called by mobile app on first launch
     * Links Android Keystore (TEE) public key to the bank account.
     * Should only be called over local network / secure channel.
     */
    @PostMapping("/bank/device/register")
    public ResponseEntity<?> registerDevice(@RequestBody DeviceRegisterRequest req) {
        try {
            BankUser user = userService.registerDevice(req.phone(), req.deviceId(), req.publicKeyBase64());
            return ResponseEntity.ok(Map.of(
                    "status", "registered",
                    "accountNumber", user.getAccountNumber(),
                    "fullName", user.getFullName(),
                    "languageCode", user.getLanguageCode()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /bank/account — called by mobile app to get account info by phone
     */
    @GetMapping("/bank/account")
    public ResponseEntity<?> getAccount(@RequestParam String phone) {
        return userService.getByPhone(phone)
                .map(u -> ResponseEntity.ok(Map.of(
                        "accountNumber", u.getAccountNumber(),
                        "fullName", u.getFullName(),
                        "balancePaise", u.getBalancePaise(),
                        "formattedBalance", u.getFormattedBalance(),
                        "languageCode", u.getLanguageCode(),
                        "deviceLinked", u.getDeviceId() != null
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    public record DeviceRegisterRequest(String phone, String deviceId, String publicKeyBase64) {}
}
