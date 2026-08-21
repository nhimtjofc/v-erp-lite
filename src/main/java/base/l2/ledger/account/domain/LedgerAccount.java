package base.l2.ledger.account.domain;

import base.l0.system.persistance.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * An account in the book.
 *
 * <p>{@code balanceMinor} and {@code entrySeq} are read-only from Java's point of view: a database
 * trigger rejects any update to them that does not come from {@code ledger_post_entry()}. They are
 * mapped here so balances can be read cheaply, not so they can be written.
 */
@Entity
@Table(name = "ledger_accounts")
@Getter
@Setter
public class LedgerAccount extends BaseEntity.Mutable {

    @Column(name = "code", nullable = false)
    private String code;

    // VARCHAR/CHAR rather than a database ENUM on all three. Hibernate 7 maps
    // @Enumerated(STRING) to a native MariaDB ENUM by default, then fails schema validation
    // against the columns Flyway actually created — an error on every boot for a correct schema.
    // Flyway owns DDL here, and a native ENUM would put the permitted values in two places at
    // once, so adding a new account class would become a migration instead of a constant.

    /** Where this sits in the accounting equation — what statement it lands on. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "account_class", nullable = false, length = 16)
    private AccountClass accountClass;

    /** What it is structurally. Orthogonal to {@link #accountClass}. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "account_role", nullable = false, length = 16)
    private AccountRole accountRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "normal_side", nullable = false, length = 2)
    private NormalSide normalSide;

    @Column(name = "owner_type")
    private String ownerType;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "control_account_uuid")
    private UUID controlAccountUuid;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Whether this account may end a posting with a debit balance — i.e. the counterparty owing us. */
    @Column(name = "allow_debit_balance", nullable = false)
    private boolean allowDebitBalance;

    @Column(name = "allow_credit_balance", nullable = false)
    private boolean allowCreditBalance = true;

    /** Ceiling on the debit balance. Only consulted when {@link #allowDebitBalance} is set. */
    @Column(name = "credit_limit_minor", nullable = false)
    private long creditLimitMinor;

    @Column(name = "payment_terms_days")
    private Short paymentTermsDays;

    @Column(name = "frozen", nullable = false)
    private boolean frozen;

    /**
     * Items posted here are expected to clear individually — money in transit, not a position we
     * hold. A standing balance on one of these is unfinished business, and
     * {@code LedgerInvariantChecker} says so.
     *
     * <p>Not every intermediate account qualifies. {@code asset:psp:*} is a real position with a
     * settlement cycle and a standing balance, and {@code liability:funds_held} is held for as long as
     * the calling system says; neither is clearing.
     */
    @Column(name = "is_clearing", nullable = false)
    private boolean clearing;

    /** How long an item may sit before it is a problem rather than a process. */
    @Column(name = "clearing_tolerance_days")
    private Short clearingToleranceDays;

    @Column(name = "balance_minor", nullable = false, insertable = false, updatable = false)
    private long balanceMinor;

    @Column(name = "entry_seq", nullable = false, insertable = false, updatable = false)
    private long entrySeq;

    /**
     * Both balance flags cleared means the account must sit at zero: any entry leaving it non-zero
     * is refused, while one that clears it still posts. That is how an account is retired without
     * deleting its history.
     */
    public boolean isRetired() {
        return !allowDebitBalance && !allowCreditBalance;
    }

    /**
     * The account with this code and currency, or {@link AccountNotFoundException}.
     *
     * <p>Deliberately a lookup and never an open-if-missing. A caller naming an account by code —
     * a manual posting, say — would otherwise be able to conjure one, and the account that appeared
     * would carry whatever balance policy the calling code happened to guess. The chart of accounts
     * is opened on purpose, through {@code OpenAccountUC}, or not at all.
     */
    public static LedgerAccount require(String code, String currency) {
        LedgerAccount account = find("code = ?1 and currency = ?2", code, currency).firstResult();
        if (account == null) {
            throw new AccountNotFoundException(code, currency);
        }
        return account;
    }
}

