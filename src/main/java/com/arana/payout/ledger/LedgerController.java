package com.arana.payout.ledger;

import com.arana.payout.auth.BearerTokenFilter;
import com.arana.payout.config.PayoutProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
        String clientId = (String) http.getAttribute(BearerTokenFilter.CLIENT_ID_ATTR);
        String account = LedgerAccounts.clientFunds(clientId);
        BigDecimal available = ledger.snapshotBalance(account); // single-row read, not a SUM
        return new BalanceResponse(account, props.seedCurrency(), available);
    }

    @GetMapping("/reconcile")
    public List<ReconItem> reconcile() {
        return ledger.reconcileAll();
    }

    public record BalanceResponse(String account, String currency, BigDecimal available) {}
}
