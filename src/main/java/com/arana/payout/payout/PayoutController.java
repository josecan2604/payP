package com.arana.payout.payout;

import com.arana.payout.auth.BearerTokenFilter;
import com.arana.payout.ledger.LedgerEntryView;
import com.arana.payout.payout.PayoutDtos.CreatePayoutRequest;
import com.arana.payout.payout.PayoutDtos.PayoutResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * The payout API. Everything here is behind the bearer-token filter (/v1/*).
 *
 *   POST /v1/payouts        -> create (201, PROCESSING), requires Idempotency-Key
 *   GET  /v1/payouts/{id}   -> read current status
 */
@RestController
@RequestMapping("/v1/payouts")
public class PayoutController {

    private final PayoutService service;

    public PayoutController(PayoutService service) {
        this.service = service;
    }

    private static String callerId(HttpServletRequest http) {
        return (String) http.getAttribute(BearerTokenFilter.CLIENT_ID_ATTR);
    }

    @PostMapping
    public ResponseEntity<PayoutResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePayoutRequest request,
            HttpServletRequest http) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingHeaderException("Idempotency-Key");
        }
        Payout payout = service.create(callerId(http), idempotencyKey, request);
        return ResponseEntity
                .created(URI.create("/v1/payouts/" + payout.id()))
                .body(PayoutResponse.from(payout));
    }

    @GetMapping("/{id}")
    public PayoutResponse get(@PathVariable String id, HttpServletRequest http) {
        return PayoutResponse.from(service.getForClient(callerId(http), id));
    }

    /** The double-entry journal lines behind this payout (commit + settlement). */
    @GetMapping("/{id}/entries")
    public List<LedgerEntryView> entries(@PathVariable String id, HttpServletRequest http) {
        return service.entriesForPayout(callerId(http), id);
    }

    /**
     * Reverse/refund a payout: posts a mirroring ledger transaction and returns the
     * funds to the client's balance. Status becomes RETURNED.
     */
    @PostMapping("/{id}/reverse")
    public PayoutResponse reverse(@PathVariable String id, HttpServletRequest http) {
        return PayoutResponse.from(service.reverse(callerId(http), id));
    }
}
