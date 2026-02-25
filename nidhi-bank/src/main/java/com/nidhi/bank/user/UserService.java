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
    public BankUser registerDevice(String phone, String deviceId, String publicKeyBase64) {
        BankUser user = userRepo.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + phone));
        if (user.getDeviceId() != null && !user.getDeviceId().equals(deviceId)) {
            throw new SecurityException("Account already linked to another device");
        }
        user.setDeviceId(deviceId);
        user.setPublicKeyBase64(publicKeyBase64);
        user.setDeviceRegisteredAt(Instant.now());
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

    // ── DTOs ───────────────────────────────────────────────────────

    public record RegisterRequest(
            String fullName,
            String phone,
            long initialBalancePaise,
            String languageCode,
            String pin
    ) {}

    public record StatsResponse(long totalUsers, long totalBalancePaise) {}
}
