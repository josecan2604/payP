package com.arana.payout.ledger;

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
    private final Clock clock;

    public LedgerService(LedgerAccountJpaRepository accounts, LedgerEntryJpaRepository entries, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
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

        for (JournalLine line : lines) {
            if (line.amount().signum() <= 0) {
                throw new UnbalancedTransactionException("line amount must be positive: " + line);
            }
            ensureAccount(line.accountId(), line.accountType(), line.currency(), now);

            BigDecimal debit = line.direction() == Direction.DEBIT ? line.amount() : BigDecimal.ZERO;
            BigDecimal credit = line.direction() == Direction.CREDIT ? line.amount() : BigDecimal.ZERO;
            entries.save(new LedgerEntryEntity(
                    UUID.randomUUID().toString(), txnId, txnType, reference,
                    line.accountId(), line.currency(),
                    debit.setScale(2, java.math.RoundingMode.HALF_UP),
                    credit.setScale(2, java.math.RoundingMode.HALF_UP),
                    now));
        }
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

    /** Available (credit-normal) balance of an account, e.g. a client's funds. */
    @Transactional(readOnly = true)
    public BigDecimal availableBalance(String accountId) {
        BigDecimal bal = entries.creditMinusDebit(accountId);
        return bal == null ? BigDecimal.ZERO : bal;
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
