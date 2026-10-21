package com.arana.payout.persistence;

import com.arana.payout.ledger.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One account in the chart of accounts. */
@Entity
@Table(name = "ledger_account")
public class LedgerAccountEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant createdAt;

    public LedgerAccountEntity() {
        // required by JPA
    }

    public LedgerAccountEntity(String id, AccountType type, String currency, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public AccountType getType() { return type; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
