package base.l2.ledger.journal.domain;

import base.l0.system.money.Money;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One side of one entry.
 *
 * <p>The three snapshot columns record what the account balance was before this line, what it
 * became, and which per-account sequence number the line consumed. A statement of account renders
 * from these directly, and the balance at any point in the <em>posting sequence</em> is a lookup
 * rather than a fold.
 *
 * <p>The balance at a point in <em>time</em> is not the same question and the snapshots cannot
 * answer it. They chain in {@code accountEntrySeq} order, so a line's snapshot includes every line
 * posted before it whatever those lines are dated — and a backdated correction, which is the
 * normal way a missed bank movement reaches the book, breaks the correspondence between the two
 * orderings. As-of-date is a fold over {@link #effectiveAt}, indexed for it by
 * {@code idx_journal_lines_account_effective}.
 */
@Entity
@Table(name = "journal_lines")
@Getter
@Setter
public class JournalLine extends BaseEntity {

    @Column(name = "entry_uuid", nullable = false)
    private UUID entryUuid;

    @Column(name = "account_uuid", nullable = false)
    private UUID accountUuid;

    // CHAR(2), not a database ENUM. Hibernate 7 maps @Enumerated(STRING) to a native MariaDB
    // ENUM by default and then fails schema validation against the CHAR column Flyway created —
    // an error logged on every boot, for a schema that is correct. The column is the authority
    // here: Flyway owns DDL and a native ENUM would put the permitted values in two places, so
    // adding one would mean an ALTER rather than a row.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "direction", nullable = false, length = 2)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "signed_minor", nullable = false)
    private long signedMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * The entry's effective date, copied down to the line so an as-of-date balance is a covering
     * index scan rather than a join. Read-only here and read-only everywhere: a BEFORE INSERT
     * trigger derives it from the entry, so nothing — this class, the posting procedure, a repair
     * script — can put a value here that disagrees with the entry it belongs to.
     */
    @Column(name = "effective_at", nullable = false, insertable = false, updatable = false)
    private Instant effectiveAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "line_no", nullable = false)
    private short lineNo;

    @Column(name = "account_prev_balance_minor", nullable = false)
    private long accountPrevBalanceMinor;

    @Column(name = "account_new_balance_minor", nullable = false)
    private long accountNewBalanceMinor;

    @Column(name = "account_entry_seq", nullable = false)
    private long accountEntrySeq;

    @Column(name = "memo")
    private String memo;

    public Money amount() {
        return Money.of(amountMinor, currency);
    }
}

