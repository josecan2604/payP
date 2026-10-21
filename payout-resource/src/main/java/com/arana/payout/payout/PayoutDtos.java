package com.arana.payout.payout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** Request/response shapes for the payout API. */
public final class PayoutDtos {

    private PayoutDtos() {}

    public record CreatePayoutRequest(
            @NotNull @Valid Amount amount,
            @NotBlank String payoutCurrency,
            @NotNull @Valid Recipient recipient,
            String reference,
            @NotBlank String purpose
    ) {}

    public record Amount(
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") BigDecimal value,
            @NotBlank String currency
    ) {}

    public record Recipient(
            @NotBlank String name,
            @NotNull @Valid Bank bank
    ) {}

    public record Bank(
            @NotBlank String country,
            @NotBlank String accountNumber,
            @NotBlank String bankCode,
            String accountType
    ) {}

    /** Mirrors the real-world response: shows the applied FX rate, fee, and lifecycle timestamps. */
    public record PayoutResponse(
            String id,
            PayoutStatus status,
            Money amount,
            Money payout,
            double fxRate,
            Money fee,
            Instant estimatedArrival,
            Instant createdAt,
            Instant completedAt
    ) {
        public static PayoutResponse from(Payout p) {
            return new PayoutResponse(
                    p.id(), p.status(), p.amount(), p.payout(), p.fxRate(), p.fee(),
                    p.estimatedArrival(), p.createdAt(), p.completedAt());
        }
    }
}
