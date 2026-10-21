package com.arana.payout.ledger;

import com.arana.payout.auth.BearerTokenFilter;
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
        String clientId = (String) http.getAttribute(BearerTokenFilter.CLIENT_ID_ATTR);
        String account = LedgerAccounts.clientFunds(clientId);
        BigDecimal available = ledger.snapshotBalance(account); // single-row read, not a SUM
        return new BalanceResponse(account, props.seedCurrency(), available);
    }

    @GetMapping("/reconcile")
    public List<ReconItem> reconcile() {
        return ledger.reconcileAll();
    }

    /**
     * Caller-scoped account statement: the entries on the client's own funds account,
     * newest first, paginated, optionally filtered by transaction type.
     *   GET /v1/ledger?page=0&size=20&type=PAYOUT_COMMIT
     */
    @GetMapping("/ledger")
    public LedgerPage ledger(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) String type,
                             HttpServletRequest http) {
        String clientId = (String) http.getAttribute(BearerTokenFilter.CLIENT_ID_ATTR);
        String account = LedgerAccounts.clientFunds(clientId); // scoped to the caller
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);       // cap to protect the server
        return ledger.statement(account, type, safePage, safeSize);
    }

    public record BalanceResponse(String account, String currency, BigDecimal available) {}
}
