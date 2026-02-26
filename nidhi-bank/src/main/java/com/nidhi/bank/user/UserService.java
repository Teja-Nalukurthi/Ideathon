package com.nidhi.bank.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final BankUserRepository userRepo;
    private final SecureRandom rng = new SecureRandom();

    public List<BankUser> getAllUsers() {
        return userRepo.findAllByOrderByCreatedAtDesc();
    }

    public Optional<BankUser> getByAccountNumber(String acc) {
        return userRepo.findByAccountNumber(acc);
    }

    public Optional<BankUser> getByPhone(String phone) {
        return userRepo.findByPhone(phone);
    }

    public Optional<BankUser> getByDeviceId(String deviceId) {
        return userRepo.findByDeviceId(deviceId);
    }

    public Optional<BankUser> getById(Long id) {
        return userRepo.findById(id);
    }

    @Transactional
    public BankUser toggleActive(Long id) {
        BankUser user = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setActive(!user.isActive());
        return userRepo.save(user);
    }

    @Transactional
    public BankUser resetDevice(Long id) {
        BankUser user = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setDeviceId(null);
        user.setPublicKeyBase64(null);
        user.setDeviceRegisteredAt(null);
        log.info("TEE/device reset for user={}", user.getFullName());
        return userRepo.save(user);
    }

    @Transactional
    public BankUser registerUser(RegisterRequest req) {
        if (userRepo.existsByPhone(req.phone())) {
            throw new IllegalArgumentException("Phone number already registered: " + req.phone());
        }

        BankUser user = new BankUser();
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone().trim());
        user.setAccountNumber(generateAccountNumber());
        user.setBalancePaise(req.initialBalancePaise());
        user.setLanguageCode(req.languageCode() != null ? req.languageCode() : "hi");
        user.setPinHash(hashPin(req.pin()));
        user.setActive(true);
        user.setCreatedAt(Instant.now());

        userRepo.save(user);
        log.info("New user registered: {} | acc={} | balance={}p",
                user.getFullName(), user.getAccountNumber(), user.getBalancePaise());
        return user;
    }

    /** Called by mobile app on first launch to link TEE public key to account */
    @Transactional
    public BankUser registerDevice(String phone, String deviceId, String publicKeyBase64, String fcmToken) {
        BankUser user = userRepo.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + phone));
        if (user.getDeviceId() != null && !user.getDeviceId().equals(deviceId)) {
            throw new SecurityException("Account already linked to another device");
        }
        user.setDeviceId(deviceId);
        user.setPublicKeyBase64(publicKeyBase64);
        user.setDeviceRegisteredAt(Instant.now());
        if (fcmToken != null && !fcmToken.isBlank()) {
            user.setFcmToken(fcmToken);
        }
        userRepo.save(user);
        log.info("Device registered for user={} deviceId={}", user.getFullName(), deviceId);
        return user;
    }

    @Transactional
    public BankUser updateBalance(String accountNumber, long newBalancePaise) {
        BankUser user = userRepo.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
        user.setBalancePaise(newBalancePaise);
        return userRepo.save(user);
    }

    @Transactional
    public void deactivateUser(Long id) {
        userRepo.findById(id).ifPresent(u -> {
            u.setActive(false);
            userRepo.save(u);
        });
    }

    /**
     * PIN-based login for the mobile app.
     * Returns the BankUser on success, throws SecurityException on failure.
     */
    public BankUser loginUser(String phone, String rawPin) {
        BankUser user = userRepo.findByPhone(phone)
                .orElseThrow(() -> new SecurityException("Invalid phone number or PIN"));
        if (!user.isActive()) {
            throw new SecurityException("Account suspended — contact your bank manager");
        }
        if (user.getPinHash() == null) {
            throw new SecurityException("No PIN set — contact your bank manager");
        }
        if (!user.getPinHash().equals(hashPin(rawPin))) {
            throw new SecurityException("Invalid phone number or PIN");
        }
        return user;
    }

    /**
     * Permanently hard-deletes a user.
     * If transferToAccount is provided, remaining balance is moved there first.
     * If null ("withdraw" / zero-balance case), the balance is simply absorbed.
     */
    @Transactional
    public void deleteUserPermanent(Long id, String transferToAccount) {
        BankUser user = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        long balance = user.getBalancePaise();
        if (balance > 0 && transferToAccount != null && !transferToAccount.isBlank()) {
            BankUser target = userRepo.findByAccountNumber(transferToAccount)
                    .or(() -> userRepo.findByPhone(transferToAccount))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Target account not found: " + transferToAccount));
            if (target.getId().equals(id)) {
                throw new IllegalArgumentException("Cannot transfer to the same account");
            }
            target.setBalancePaise(target.getBalancePaise() + balance);
            userRepo.save(target);
            log.info("Transferred {}p from deleted user {} to {}",
                    balance, user.getFullName(), target.getFullName());
        }
        userRepo.deleteById(id);
        log.info("User permanently deleted: {} | id={} | balance was={}p | transferredTo={}",
                user.getFullName(), id, balance, transferToAccount);
    }

    public StatsResponse getStats() {
        long totalUsers = userRepo.countByActive(true);
        Long totalBalance = userRepo.sumAllBalances();
        return new StatsResponse(totalUsers, totalBalance != null ? totalBalance : 0L);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private String generateAccountNumber() {
        String acc;
        do {
            long num = (long)(rng.nextDouble() * 9_000_000_000L) + 1_000_000_000L;
            acc = "NIDHI" + num;
        } while (userRepo.findByAccountNumber(acc).isPresent());
        return acc;
    }

    private String hashPin(String pin) {
        if (pin == null || pin.isBlank()) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(pin.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) { return null; }
    }

    /** Admin: update editable profile fields — phone must remain unique if changed */
    @Transactional
    public BankUser updateProfile(Long id, String fullName, String phone, String languageCode, String newPin) {
        BankUser user = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        if (phone != null && !phone.isBlank()) {
            String trimmedPhone = phone.trim();
            if (!trimmedPhone.equals(user.getPhone())) {
                if (userRepo.existsByPhone(trimmedPhone)) {
                    throw new IllegalArgumentException("Phone number already in use: " + trimmedPhone);
                }
                user.setPhone(trimmedPhone);
            }
        }
        if (languageCode != null && !languageCode.isBlank()) {
            user.setLanguageCode(languageCode.trim());
        }
        if (newPin != null && !newPin.isBlank()) {
            if (newPin.length() < 4) throw new IllegalArgumentException("PIN must be at least 4 digits");
            user.setPinHash(hashPin(newPin));
        }
        log.info("Profile updated for user={} id={}", user.getFullName(), id);
        return userRepo.save(user);
    }

    // ── DTOs ───────────────────────────────────────────────────────

    public record RegisterRequest(
            String fullName,
            String phone,
            long initialBalancePaise,
            String languageCode,
            String pin
    ) {}

    public record EditProfileRequest(
            String fullName,
            String phone,
            String languageCode,
            String newPin
    ) {}

    public record StatsResponse(long totalUsers, long totalBalancePaise) {}
}
