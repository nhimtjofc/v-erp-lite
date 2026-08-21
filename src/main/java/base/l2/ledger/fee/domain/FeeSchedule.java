package base.l2.ledger.fee.domain;

import base.l0.system.money.Money;
import base.l0.system.util.UuidUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * One version of one fee arrangement, in force over a half-open window.
 *
 * <p>Immutable once written, enforced by {@code trg_fee_schedules_immutable}. The only permitted
 * edit is closing the open end, which is how the next version starts; everything else would restate
 * what was already charged under this one.
 */
@Entity
@Table(name = "fee_schedules")
@Getter
@Setter
public class FeeSchedule extends PanacheEntityBase {

    @Id
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @Column(name = "tenant_code")
    private String tenantCode;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "basis_points", nullable = false)
    private int basisPoints;

    @Column(name = "fixed_minor", nullable = false)
    private long fixedMinor;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * What this schedule charges on {@code gross}.
     *
     * <p>Computed in {@link BigInteger} then rounded half-up. The intermediate
     * {@code gross × basisPoints} overflows a {@code long} at around 92 trillion minor units, which
     * for a zero-decimal currency is a reachable order value rather than a theoretical one — VND
     * trades near 25,000 to the euro. Half-up rather than truncation because truncating always
     * favours the same party, and a systematic bias of half a minor unit per transaction is exactly
     * the kind of thing that is invisible per order and adds up to a reconciliation argument.
     *
     * <p>Rounding is on the percentage component only; the fixed part is already exact.
     */
    public Money feeOn(Money gross) {
        BigInteger scaled = BigInteger.valueOf(gross.amountMinor())
                .multiply(BigInteger.valueOf(basisPoints));
        BigInteger[] divided = scaled.divideAndRemainder(BigInteger.valueOf(10_000));
        long percentage = divided[0].longValueExact();
        if (divided[1].abs().multiply(BigInteger.TWO).compareTo(BigInteger.valueOf(10_000)) >= 0) {
            percentage += scaled.signum() < 0 ? -1 : 1;
        }
        return Money.of(percentage + fixedMinor, gross.currency());
    }

    /** A human-readable statement of the rate, for the entry metadata and for an invoice line. */
    public String describe() {
        return code + " v" + version + " (" + basisPoints + "bp"
                + (fixedMinor == 0 ? "" : " + " + fixedMinor + " " + currency) + ")";
    }

    @PrePersist
    void assignUuid() {
        if (uuid == null) {
            uuid = UuidUtil.v7();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
