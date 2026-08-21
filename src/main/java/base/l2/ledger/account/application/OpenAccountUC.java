package base.l2.ledger.account.application;

import base.l0.system.money.CurrencyExponents;
import base.l0.system.security.CallerContext;
import base.l2.ledger.account.domain.AccountClass;
import base.l2.ledger.account.domain.AccountRole;
import base.l2.ledger.account.domain.LedgerAccount;
import base.l2.ledger.account.domain.NormalSide;
import base.l0.system.util.UuidUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Opens a ledger account, or returns the existing one.
 *
 * <p>The balance policy is set here and nowhere else, because it is the decision that determines
 * whether an account can ever express the counterparty owing us. Trading sub-ledgers opt into a
 * debit balance with an explicit limit; custodial ones do not, because a custodial balance going
 * negative means we have spent money we were holding.
 *
 * <p>The factories below are named for the balance policy they apply, never for the kind of party
 * the caller has in mind. {@code ownerType} is a free string all the way down to the column, so a
 * new counterparty kind needs rows rather than a method here.
 */
@ApplicationScoped
public class OpenAccountUC {

    @Inject
    CallerContext caller;

    @Transactional
    public LedgerAccount open(Spec spec) {
        CurrencyExponents.of(spec.currency());

        LedgerAccount existing = LedgerAccount
                .find("code = ?1 and currency = ?2 and tenantCode = ?3", spec.code(), spec.currency(), caller.tenantCode())
                .firstResult();
        if (existing != null) {
            return existing;
        }

        LedgerAccount account = new LedgerAccount();
        account.setUuid(UuidUtil.v7());
        account.setTenantCode(caller.tenantCode());
        account.setCode(spec.code());
        account.setAccountClass(spec.accountClass());
        account.setAccountRole(spec.role());
        account.setNormalSide(spec.normalSide());
        account.setOwnerType(spec.ownerType());
        account.setOwnerId(spec.ownerId());
        account.setControlAccountUuid(spec.controlAccountUuid());
        account.setExternalRef(spec.externalRef());
        account.setCurrency(spec.currency());
        account.setAllowDebitBalance(spec.allowDebitBalance());
        account.setAllowCreditBalance(spec.allowCreditBalance());
        account.setCreditLimitMinor(spec.creditLimitMinor());
        account.setPaymentTermsDays(spec.paymentTermsDays());
        account.setClearing(spec.clearing());
        account.setClearingToleranceDays(spec.clearingToleranceDays());
        account.setCreatedBy(caller.actorId());
        account.persist();
        return account;
    }

    public record Spec(
            String code,
            AccountClass accountClass,
            AccountRole role,
            NormalSide normalSide,
            String ownerType,
            UUID ownerId,
            UUID controlAccountUuid,
            String externalRef,
            String currency,
            boolean allowDebitBalance,
            boolean allowCreditBalance,
            long creditLimitMinor,
            Short paymentTermsDays,
            boolean clearing,
            Short clearingToleranceDays
    ) {
        /** A partner we owe, who may also come to owe us up to {@code creditLimitMinor}. */
        public static Spec partner(String code, String ownerType, UUID ownerId, String currency,
                                   UUID control, long creditLimitMinor, Short termsDays) {
            return new Spec(code, AccountClass.LIABILITY, AccountRole.SUBLEDGER, NormalSide.CR,
                    ownerType, ownerId, control, null, currency, true, true, creditLimitMinor, termsDays,
                    false, null);
        }

        /**
         * Money we hold on someone's behalf. Never allowed to go debit: a custodial balance below
         * zero means we have spent funds that were not ours to spend.
         */
        public static Spec custodial(String code, String ownerType, UUID ownerId, String currency,
                                     UUID control) {
            return new Spec(code, AccountClass.LIABILITY, AccountRole.SUBLEDGER, NormalSide.CR,
                    ownerType, ownerId, control, null, currency, false, true, 0L, null, false, null);
        }

        /**
         * A position we hold somewhere external and can draw on — a bank account, a balance at a
         * provider, per-currency liquidity. Normally debit, because it is ours.
         */
        public static Spec asset(String code, String currency, String externalRef) {
            return new Spec(code, AccountClass.ASSET, AccountRole.GENERAL, NormalSide.DR,
                    null, null, null, externalRef, currency, true, true, Long.MAX_VALUE, null, false, null);
        }

        /**
         * An obligation held in a control account rather than against a named counterparty — money
         * in flight to a bank, funds held for someone, receipts not yet explained. Normally credit.
         */
        public static Spec liability(String code, String currency, String externalRef) {
            return new Spec(code, AccountClass.LIABILITY, AccountRole.GENERAL, NormalSide.CR,
                    null, null, null, externalRef, currency, true, true, Long.MAX_VALUE, null, false, null);
        }

        /**
         * Money in transit rather than a position we hold: instructed and unconfirmed, received and
         * unexplained. Items here are expected to clear one by one, and
         * {@code LedgerInvariantChecker} reports any lifecycle that leaves something behind for
         * longer than {@code toleranceDays}.
         *
         * <p>This is the flag rather than a naming convention on purpose. "Should trend to zero"
         * used to be a property of the account code, known only to whoever had read
         * {@code AccountCode}, so a new clearing account was invisible to the checks until someone
         * remembered to widen a {@code LIKE} pattern.
         */
        public static Spec clearingLiability(String code, String currency, String externalRef,
                                           short toleranceDays) {
            return new Spec(code, AccountClass.LIABILITY, AccountRole.GENERAL, NormalSide.CR,
                    null, null, null, externalRef, currency, true, true, Long.MAX_VALUE, null,
                    true, toleranceDays);
        }

        /**
         * The asset-side counterpart of {@link #clearingLiability}: a claim in transit rather than an
         * obligation in transit.
         *
         * <p>The distinction is not bookkeeping pedantry. A clearing account's whole job is to say
         * "this is unfinished", and which side it sits on says what kind of unfinished. Money we
         * have told a bank to send is an obligation we have not yet discharged. Money an acquirer
         * has provisionally taken back from us is value we may still recover. Filing the second
         * under the first would leave a liability account carrying a debit balance for weeks and
         * make every position-by-class report read the wrong way round.
         */
        public static Spec clearingAsset(String code, String currency, String externalRef,
                                         short toleranceDays) {
            return new Spec(code, AccountClass.ASSET, AccountRole.GENERAL, NormalSide.DR,
                    null, null, null, externalRef, currency, true, true, Long.MAX_VALUE, null,
                    true, toleranceDays);
        }

        /**
         * The account a subsidiary ledger totals into. Never posted to directly —
         * {@code LedgerInvariantChecker} reports it if anything does.
         *
         * <p>This is what accounting means by a control account, and the only factory here that
         * produces one. The others make ordinary general-ledger accounts, however external or
         * temporary the thing they represent.
         */
        public static Spec control(String code, String currency) {
            return new Spec(code, AccountClass.LIABILITY, AccountRole.CONTROL, NormalSide.CR,
                    null, null, null, null, currency, true, true, Long.MAX_VALUE, null, false, null);
        }

        public static Spec revenue(String code, String currency) {
            return new Spec(code, AccountClass.REVENUE, AccountRole.GENERAL, NormalSide.CR,
                    null, null, null, null, currency, false, true, 0L, null, false, null);
        }

        public static Spec expense(String code, String currency) {
            return new Spec(code, AccountClass.EXPENSE, AccountRole.GENERAL, NormalSide.DR,
                    null, null, null, null, currency, true, false, Long.MAX_VALUE, null, false, null);
        }
    }
}
