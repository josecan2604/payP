package com.arana.payout.ledger;

import com.arana.payout.auth.ForwardedIdentityFilter;
import com.arana.payout.config.PayoutProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /v1/balance    -> the calling client's available funds (fast snapshot read).
 * GET /v1/reconcile  -> re-sums the journal and compares it to the cached snapshots.
 * Behind the bearer-token filter; the client id comes from the validated token.
 */
@RestController
@RequestMapping("/v1")
public class LedgerController {

    private final LedgerService ledger;
    private final PayoutProperties props;

    public LedgerController(LedgerService ledger, PayoutProperties props) {
        this.ledger = ledger;
        this.props = props;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(HttpServletRequest http) {
        BigDecimal available = ledger.availableFundsForClient(callerId(http));
        return new BalanceResponse(props.seedCurrency(), available);
    }

    @GetMapping("/reconcile")
    public List<ReconItem> reconcile() {
        return ledger.reconcileAll();
    }

    /**
     * The caller's own account statement, newest first, paginated, optionally filtered by
     * transaction type:  GET /v1/ledger?page=0&size=20&type=PAYOUT_COMMIT
     */
    @GetMapping("/ledger")
    public LedgerPage ledger(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) String type,
                             HttpServletRequest http) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100); // cap to protect the server
        return ledger.statementForClient(callerId(http), type, safePage, safeSize);
    }

    private static String callerId(HttpServletRequest http) {
        return (String) http.getAttribute(ForwardedIdentityFilter.CLIENT_ID_ATTR);
    }

    public record BalanceResponse(String currency, BigDecimal available) {}
}
