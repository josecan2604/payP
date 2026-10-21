package com.arana.payout.ledger;

import com.arana.payout.persistence.AccountBalanceEntity;
import com.arana.payout.persistence.AccountBalanceJpaRepository;
import com.arana.payout.persistence.AccountBalanceProjection;
import com.arana.payout.persistence.LedgerAccountEntity;
import com.arana.payout.persistence.LedgerAccountJpaRepository;
import com.arana.payout.persistence.LedgerEntryEntity;
import com.arana.payout.persistence.LedgerEntryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The double-entry engine. Its one invariant: every posted transaction is balanced
 * (sum of debits == sum of credits, per currency). Because of that invariant, the
 * whole system can never create or destroy money — only move it between accounts.
 */
@Service
public class LedgerService {

    private final LedgerAccountJpaRepository accounts;
    private final LedgerEntryJpaRepository entries;
    private final AccountBalanceJpaRepository balances;
    private final Clock clock;

    public LedgerService(LedgerAccountJpaRepository accounts, LedgerEntryJpaRepository entries,
                         AccountBalanceJpaRepository balances, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.balances = balances;
        this.clock = clock;
    }

    /** Post a balanced transaction, tagging every line with a business reference. Throws if unbalanced. */
    @Transactional
    public String post(String txnType, String reference, List<JournalLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new UnbalancedTransactionException("a transaction needs at least two lines");
        }
        assertBalanced(lines);

        String txnId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Map<String, BigDecimal> deltaByAccount = new LinkedHashMap<>();

        for (JournalLine line : lines) {
            if (line.amount().signum() <= 0) {
                throw new UnbalancedTransactionException("line amount must be positive: " + line);
            }
            ensureAccount(line.accountId(), line.accountType(), line.currency(), now);

            BigDecimal amount = line.amount().setScale(2, java.math.RoundingMode.HALF_UP);
            boolean isDebit = line.direction() == Direction.DEBIT;
            entries.save(new LedgerEntryEntity(
                    UUID.randomUUID().toString(), txnId, txnType, reference,
                    line.accountId(), line.currency(),
                    isDebit ? amount : BigDecimal.ZERO,
                    isDebit ? BigDecimal.ZERO : amount,
                    now));

            // Credit-normal delta: credit adds, debit subtracts.
            deltaByAccount.merge(line.accountId(), isDebit ? amount.negate() : amount, BigDecimal::add);
        }

        // Update the running-balance snapshot once per account, in this same transaction.
        deltaByAccount.forEach((accountId, delta) -> applyToSnapshot(accountId, delta, now));
        return txnId;
    }

    /**
     * Post the mirror image of a set of entries (debit <-> credit) as one PAYOUT_REVERSAL
     * transaction. Reversing a balanced set is itself balanced, so money returns exactly
     * to where it was before.
     */
    @Transactional
    public String postReversal(String reference, List<LedgerEntryEntity> originals) {
        List<JournalLine> lines = new ArrayList<>();
        for (LedgerEntryEntity e : originals) {
            AccountType type = accounts.findById(e.getAccountId())
                    .map(LedgerAccountEntity::getType)
                    .orElse(AccountType.ASSET); // accounts always pre-exist for a reversal
            if (e.getDebit().signum() > 0) {
                lines.add(JournalLine.credit(e.getAccountId(), type, e.getDebit(), e.getCurrency()));
            } else {
                lines.add(JournalLine.debit(e.getAccountId(), type, e.getCredit(), e.getCurrency()));
            }
        }
        return post("PAYOUT_REVERSAL", reference, lines);
    }

    /**
     * Fast read: the account's cached balance (single-row lookup), not a sum of the journal.
     * This is what GET /balance and the funds check use.
     */
    @Transactional(readOnly = true)
    public BigDecimal snapshotBalance(String accountId) {
        return balances.findById(accountId).map(AccountBalanceEntity::getBalance).orElse(BigDecimal.ZERO);
    }

    /** Authoritative: the credit-normal balance computed straight from the journal. */
    @Transactional(readOnly = true)
    public BigDecimal availableBalance(String accountId) {
        BigDecimal bal = entries.creditMinusDebit(accountId);
        return bal == null ? BigDecimal.ZERO : bal;
    }

    /**
     * Re-sum the journal per account and compare each to its cached snapshot. Every row
     * should match; a mismatch means the cache drifted and must be rebuilt from the journal.
     */
    @Transactional(readOnly = true)
    public List<ReconItem> reconcileAll() {
        Map<String, BigDecimal> snap = new LinkedHashMap<>();
        for (AccountBalanceEntity b : balances.findAll()) {
            snap.put(b.getAccountId(), b.getBalance());
        }
        List<ReconItem> result = new ArrayList<>();
        for (AccountBalanceProjection p : entries.computeAllBalances()) {
            BigDecimal computed = p.getBalance().setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal cached = snap.getOrDefault(p.getAccountId(), BigDecimal.ZERO);
            result.add(new ReconItem(p.getAccountId(), cached, computed, cached.compareTo(computed) == 0));
        }
        return result;
    }

    private void applyToSnapshot(String accountId, BigDecimal delta, Instant now) {
        int updated = balances.addToBalance(accountId, delta, now);
        if (updated == 0) {
            balances.save(new AccountBalanceEntity(accountId, delta, now));
        }
    }

    public boolean hasEntries(String accountId) {
        return entries.countByAccountId(accountId) > 0;
    }

    /** All journal lines posted for a reference (e.g. a payout id), oldest first. */
    @Transactional(readOnly = true)
    public List<LedgerEntryView> entriesForReference(String reference) {
        return entries.findByReferenceOrderByCreatedAtAscIdAsc(reference)
                .stream().map(LedgerEntryView::from).toList();
    }

    // --- internals -----------------------------------------------------------

    private void assertBalanced(List<JournalLine> lines) {
        Map<String, BigDecimal> debitByCcy = new LinkedHashMap<>();
        Map<String, BigDecimal> creditByCcy = new LinkedHashMap<>();
        for (JournalLine l : lines) {
            Map<String, BigDecimal> target = l.direction() == Direction.DEBIT ? debitByCcy : creditByCcy;
            target.merge(l.currency(), l.amount(), BigDecimal::add);
        }
        for (String ccy : debitByCcy.keySet()) {
            BigDecimal d = debitByCcy.getOrDefault(ccy, BigDecimal.ZERO);
            BigDecimal c = creditByCcy.getOrDefault(ccy, BigDecimal.ZERO);
            if (d.compareTo(c) != 0) {
                throw new UnbalancedTransactionException(
                        "unbalanced in " + ccy + ": debit=" + d + " credit=" + c);
            }
        }
        if (!debitByCcy.keySet().equals(creditByCcy.keySet())) {
            throw new UnbalancedTransactionException("debit/credit currencies do not match");
        }
    }

    private void ensureAccount(String id, AccountType type, String currency, Instant now) {
        if (!accounts.existsById(id)) {
            accounts.save(new LedgerAccountEntity(id, type, currency, now));
        }
    }
}
