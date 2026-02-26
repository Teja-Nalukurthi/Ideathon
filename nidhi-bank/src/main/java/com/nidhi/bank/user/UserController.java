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

    /** GET /admin/users/{id} — full user profile */
    @GetMapping("/admin/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return userService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /admin/users/{id} — deactivate user */
    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(Map.of("status", "deactivated"));
    }

    /** PUT /admin/users/{id} — edit profile fields (name, phone, language, PIN) */
    @PutMapping("/admin/users/{id}")
    public ResponseEntity<?> updateProfile(@PathVariable Long id, @RequestBody UserService.EditProfileRequest req) {
        try {
            BankUser u = userService.updateProfile(id, req.fullName(), req.phone(), req.languageCode(), req.newPin());
            return ResponseEntity.ok(u);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** PUT /admin/users/{id}/toggle — activate or deactivate account — returns full user */
    @PutMapping("/admin/users/{id}/toggle")
    public ResponseEntity<?> toggleUser(@PathVariable Long id) {
        try {
            BankUser u = userService.toggleActive(id);
            return ResponseEntity.ok(u);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** PUT /admin/users/{id}/reset-device — wipe TEE/device link — returns full user */
    @PutMapping("/admin/users/{id}/reset-device")
    public ResponseEntity<?> resetDevice(@PathVariable Long id) {
        try {
            BankUser u = userService.resetDevice(id);
            return ResponseEntity.ok(u);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /bank/device/register — called by mobile app on first launch
     * Links Android Keystore (TEE) public key to the bank account.
     * Should only be called over local network / secure channel.
     */
    @PostMapping("/bank/device/register")
    public ResponseEntity<?> registerDevice(@RequestBody DeviceRegisterRequest req) {
        try {
            BankUser user = userService.registerDevice(req.phone(), req.deviceId(), req.publicKeyBase64(), req.fcmToken());
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

    /** POST /admin/users/{id}/delete-permanent — hard delete with optional fund transfer */
    @PostMapping("/admin/users/{id}/delete-permanent")
    public ResponseEntity<?> deleteUserPermanent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            userService.deleteUserPermanent(id, body.get("transferToAccount"));
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /bank/auth/login — PIN-based login for the mobile app */
    @PostMapping("/bank/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            BankUser user = userService.loginUser(req.phone(), req.pin());
            return ResponseEntity.ok(Map.of(
                    "success",       true,
                    "phone",         user.getPhone(),
                    "fullName",      user.getFullName(),
                    "accountNumber", user.getAccountNumber(),
                    "languageCode",  user.getLanguageCode() != null ? user.getLanguageCode() : "hi",
                    "balancePaise",  user.getBalancePaise()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Login failed"));
        }
    }

    public record LoginRequest(String phone, String pin) {}

    public record DeviceRegisterRequest(String phone, String deviceId, String publicKeyBase64, String fcmToken) {}
}
