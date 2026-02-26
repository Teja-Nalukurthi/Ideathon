package com.nidhi.bank.user;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "bank_users")
@Data
@NoArgsConstructor
public class BankUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private long balancePaise;   // stored in paise (1 rupee = 100 paise)

    @Column(nullable = false)
    private String languageCode; // hi, te, ta, kn, ...

    // TEE: set when user first activates the app on their device
    @Column
    private String deviceId;

    // TEE public key from Android Keystore — set during device registration
    @Column(length = 1000)
    private String publicKeyBase64;

    // PIN hash (SHA-256 of 4-digit PIN) — set during registration
    @Column
    private String pinHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant deviceRegisteredAt;

    // FCM push token from mobile app (updated when device registers)
    @Column(length = 512)
    private String fcmToken;

    // Convenience: balance in rupees for display
    @Transient
    public String getFormattedBalance() {
        long rupees = balancePaise / 100;
        long paise  = balancePaise % 100;
        return paise > 0
                ? String.format("₹%,d.%02d", rupees, paise)
                : String.format("₹%,d", rupees);
    }
}
