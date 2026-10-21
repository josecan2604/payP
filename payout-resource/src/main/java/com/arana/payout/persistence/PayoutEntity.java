package com.arana.payout.persistence;

import com.arana.payout.payout.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** JPA mapping for the "payout" table. Columns are snake_case (Spring's default). */
@Entity
@Table(name = "payout")
public class PayoutEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amountValue;
    @Column(nullable = false, length = 3)
    private String amountCurrency;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal payoutValue;
    @Column(nullable = false, length = 3)
    private String payoutCurrency;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal fxRate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal feeValue;
    @Column(nullable = false, length = 3)
    private String feeCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status;

    private String reference;

    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant estimatedArrival;
    private Instant completedAt;

    public PayoutEntity() {
        // required by JPA; also used by the mapping layer
    }

    // getters / setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public BigDecimal getAmountValue() { return amountValue; }
    public void setAmountValue(BigDecimal amountValue) { this.amountValue = amountValue; }
    public String getAmountCurrency() { return amountCurrency; }
    public void setAmountCurrency(String amountCurrency) { this.amountCurrency = amountCurrency; }
    public BigDecimal getPayoutValue() { return payoutValue; }
    public void setPayoutValue(BigDecimal payoutValue) { this.payoutValue = payoutValue; }
    public String getPayoutCurrency() { return payoutCurrency; }
    public void setPayoutCurrency(String payoutCurrency) { this.payoutCurrency = payoutCurrency; }
    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal fxRate) { this.fxRate = fxRate; }
    public BigDecimal getFeeValue() { return feeValue; }
    public void setFeeValue(BigDecimal feeValue) { this.feeValue = feeValue; }
    public String getFeeCurrency() { return feeCurrency; }
    public void setFeeCurrency(String feeCurrency) { this.feeCurrency = feeCurrency; }
    public PayoutStatus getStatus() { return status; }
    public void setStatus(PayoutStatus status) { this.status = status; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getEstimatedArrival() { return estimatedArrival; }
    public void setEstimatedArrival(Instant estimatedArrival) { this.estimatedArrival = estimatedArrival; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
