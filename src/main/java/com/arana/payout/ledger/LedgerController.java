package com.arana.payout.ledger;

import com.arana.payout.auth.BearerTokenFilter;
import com.arana.payout.config.PayoutProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * GET /v1/balance -> the calling client's available funds.
 * Behind the bearer-token filter; the client id comes from the validated token.
 */
@RestController
@RequestMapping("/v1/balance")
public class LedgerController {

    private final LedgerService ledger;
    private final PayoutProperties props;

    public LedgerController(LedgerService ledger, PayoutProperties props) {
        this.ledger = ledger;
        this.props = props;
    }

    @GetMapping
    public BalanceResponse balance(HttpServletRequest http) {
        String clientId = (String) http.getAttribute(BearerTokenFilter.CLIENT_ID_ATTR);
        String account = LedgerAccounts.clientFunds(clientId);
        BigDecimal available = ledger.availableBalance(account);
        return new BalanceResponse(account, props.seedCurrency(), available);
    }

    public record BalanceResponse(String account, String currency, BigDecimal available) {}
}
