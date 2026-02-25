package com.nidhi.bank.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankUserRepository extends JpaRepository<BankUser, Long> {

    Optional<BankUser> findByPhone(String phone);
    Optional<BankUser> findByAccountNumber(String accountNumber);
    Optional<BankUser> findByDeviceId(String deviceId);

    @Query("SELECT u FROM BankUser u WHERE LOWER(u.fullName) = LOWER(?1)")
    Optional<BankUser> findByFullNameIgnoreCase(String fullName);

    List<BankUser> findAllByOrderByCreatedAtDesc();

    boolean existsByPhone(String phone);

    @Query("SELECT SUM(u.balancePaise) FROM BankUser u WHERE u.active = true")
    Long sumAllBalances();

    long countByActive(boolean active);
}
