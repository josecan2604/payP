package com.arana.payout.ledger;

import com.arana.payout.config.PayoutProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gives each configured client a starting balance on boot, so the demo has funds
 * to pay out. The funding transaction is itself double-entry:
 *   DEBIT  BANK_CASH              (money arrives in our bank)
 *   CREDIT CLIENT_FUNDS:<client>  (we now owe it to the client)
 *
 * Idempotent: skips any client that already has ledger entries.
 */
@Component
public class FundingSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FundingSeeder.class);

    private final LedgerService ledger;
    private final PayoutProperties props;

    public FundingSeeder(LedgerService ledger, PayoutProperties props) {
        this.ledger = ledger;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.clients() == null) {
            return;
        }
        BigDecimal seed = BigDecimal.valueOf(props.seedBalance()).setScale(2, java.math.RoundingMode.HALF_UP);
        String ccy = props.seedCurrency();

        for (String clientId : props.clients().keySet()) {
            String account = LedgerAccounts.clientFunds(clientId);
            if (ledger.hasEntries(account)) {
                continue;
            }
            ledger.post("FUNDING", "seed:" + clientId, List.of(
                    JournalLine.debit(LedgerAccounts.BANK_CASH, AccountType.ASSET, seed, ccy),
                    JournalLine.credit(account, AccountType.LIABILITY, seed, ccy)
            ));
            log.info("seeded {} with {} {}", clientId, seed, ccy);
        }
    }
}
