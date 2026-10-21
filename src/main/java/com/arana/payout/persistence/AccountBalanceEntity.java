package com.arana.payout.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Cached credit-normal balance (sum(credit) - sum(debit)) for one account. */
@Entity
@Table(name = "account_balance")
public class AccountBalanceEntity {

    @Id
    private String accountId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private Instant updatedAt;

    public AccountBalanceEntity() {
        // required by JPA
    }

    public AccountBalanceEntity(String accountId, BigDecimal balance, Instant updatedAt) {
        this.accountId = accountId;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public Instant getUpdatedAt() { return updatedAt; }
}
