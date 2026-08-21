package base.l2.recon.bank.application;

import base.l0.system.money.Money;
import base.l0.system.security.CallerContext;
import base.l0.system.util.UuidUtil;
import base.l2.ledger.account.domain.AccountClass;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.recon.bank.domain.BankStatementAttestation;
import base.l2.recon.bank.domain.BankTransactionRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;

/**
 * Records what the bank says it held on a date.
 *
 * <p>This posts nothing. It is not a movement, it is a witness statement — and keeping it out of
 * the journal is what makes it useful: an attestation that adjusted the balance to match would be
 * a plug, and a book that plugs itself can no longer tell you it is wrong.
 *
 * <p>At the volume a platform-dedicated account sees, this is deliberately cheaper than importing
 * every statement line. Platform activity is complete by construction, so any gap between this
 * number and our own <em>is</em> the off-platform activity, and it is a short enough list to
 * identify by eye against the statement. Line-level matching is worth building when the eye stops
 * being able to keep up, and not before.
 */
@ApplicationScoped
public class AttestBankBalanceUC {

    @Inject
    CallerContext caller;

    /**
     * Stores the statement's closing balance, replacing any attestation already held for that date
     * — a bank reissuing a corrected statement should overwrite, not accumulate. Nothing in the
     * journal depends on the previous value.
     */
    @Transactional
    public BankStatementAttestation attest(String bankAccountCode, LocalDate statementDate,
                                           Money closingBalance, String statementRef, String note) {
        if (statementDate == null) {
            throw new BankTransactionRejectedException("An attestation needs the date the statement closes");
        }
        if (statementDate.isAfter(LocalDate.now())) {
            throw new BankTransactionRejectedException(
                    "Statement date " + statementDate + " is in the future; no bank has confirmed it yet");
        }

        LedgerAccount account = LedgerAccount.require(bankAccountCode, closingBalance.currency());
        if (account.getAccountClass() != AccountClass.ASSET) {
            throw new BankTransactionRejectedException(
                    bankAccountCode + " is a " + account.getAccountClass()
                            + " account; only a position we hold somewhere can be attested by whoever holds it");
        }
        BankStatementAttestation existing = BankStatementAttestation
                .find("accountUuid = ?1 and statementDate = ?2 and tenantCode = ?3", account.getUuid(), statementDate, caller.tenantCode())
                .firstResult();

        BankStatementAttestation attestation = existing != null ? existing : new BankStatementAttestation();
        if (existing == null) {
            attestation.setUuid(UuidUtil.v7());
            attestation.setTenantCode(caller.tenantCode());
            attestation.setAccountUuid(account.getUuid());
            attestation.setStatementDate(statementDate);
            attestation.setCurrency(account.getCurrency());
            attestation.setCreatedBy(caller.actorId());
        } else {
            attestation.setUpdatedBy(caller.actorId());
        }
        attestation.setClosingBalanceMinor(closingBalance.amountMinor());
        attestation.setStatementRef(statementRef);
        attestation.setNote(note);
        attestation.persist();
        return attestation;
    }
}
