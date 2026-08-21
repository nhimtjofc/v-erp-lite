package base.l2.recon.bank.domain;

import base.l0.system.persistance.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What the bank says it held on a given date.
 *
 * <p>The one fact in this service that does not originate here. Everything else is a consequence of
 * an entry we posted and can be re-derived from the journal; this is testimony from outside, and it
 * is the only thing that can tell us the book is incomplete rather than merely self-consistent.
 *
 * <p>Records the bank's claim and nothing else. Our balance on that date and the difference between
 * them are computed by {@code v_bank_attestation_delta}, so that booking the missing transactions
 * closes the gap on its own rather than leaving a stored number that has to be recalculated by
 * somebody remembering to.
 */
@Entity
@Table(name = "bank_statement_attestations")
@Getter
@Setter
public class BankStatementAttestation extends BaseEntity.Mutable {

    @Column(name = "account_uuid", nullable = false)
    private UUID accountUuid;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    /** Debit-positive, matching the ledger: for a bank asset account, positive means funds held. */
    @Column(name = "closing_balance_minor", nullable = false)
    private long closingBalanceMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "statement_ref")
    private String statementRef;

    @Column(name = "note")
    private String note;
}

