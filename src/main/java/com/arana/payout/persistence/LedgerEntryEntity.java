package com.arana.payout.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One immutable journal line. Entries are never updated or deleted — corrections
 * are made by posting a new, reversing transaction. Each row carries exactly one
 * non-zero side (debit XOR credit); both are stored so sums are trivial.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntryEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String txnId;      // groups the lines of one balanced transaction

    @Column(nullable = false)
    private String txnType;    // e.g. FUNDING, PAYOUT_COMMIT, PAYOUT_SETTLE

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal credit;

    @Column(nullable = false)
    private Instant createdAt;

    public LedgerEntryEntity() {
        // required by JPA
    }

    public LedgerEntryEntity(String id, String txnId, String txnType, String accountId,
                             String currency, BigDecimal debit, BigDecimal credit, Instant createdAt) {
        this.id = id;
        this.txnId = txnId;
        this.txnType = txnType;
        this.accountId = accountId;
        this.currency = currency;
        this.debit = debit;
        this.credit = credit;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getTxnId() { return txnId; }
    public String getTxnType() { return txnType; }
    public String getAccountId() { return accountId; }
    public String getCurrency() { return currency; }
    public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; }
    public Instant getCreatedAt() { return createdAt; }
}
