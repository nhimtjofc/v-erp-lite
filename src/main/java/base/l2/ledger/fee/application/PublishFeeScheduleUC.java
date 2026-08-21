package base.l2.ledger.fee.application;

import base.l0.system.money.Money;
import base.l0.system.security.CallerContext;
import base.l2.ledger.fee.domain.FeeSchedule;
import base.l2.ledger.fee.domain.FeeScheduleException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Publishes the next version of a fee arrangement.
 *
 * <p>Never edits. Publishing closes the window of whatever was in force and opens a new one from
 * the same instant, so the two abut exactly and no capture can fall between them or into both. A
 * rate change is therefore additive, and what was charged last month stays computable from the row
 * that charged it.
 */
@ApplicationScoped
public class PublishFeeScheduleUC {

    @Inject
    FeeScheduleQuery schedules;

    @Inject
    CallerContext caller;

    @Transactional
    public FeeSchedule publish(String code, Instant effectiveFrom, int basisPoints,
                               Money fixed, String note) {
        if (code == null || code.isBlank()) {
            throw new FeeScheduleException("A fee schedule needs a code");
        }
        if (effectiveFrom == null) {
            throw new FeeScheduleException("A fee schedule version needs the instant it takes effect");
        }
        if (basisPoints < 0) {
            throw new FeeScheduleException("A negative rate is a rebate, and is not a fee schedule");
        }
        if (fixed != null && fixed.amountMinor() < 0) {
            throw new FeeScheduleException("A negative fixed component is a rebate, and is not a fee schedule");
        }

        // The readable half of a rule the database also enforces: trg_fee_schedules_forward_only
        // refuses the same insert, so a repair script or a second writer cannot slip past this.
        // A schedule is a sequence, and versions only go forward. Without it a version dated
        // before an existing one is inserted open-ended with nothing to close, and two versions
        // then claim the same instant: publish January, publish March, publish the previous June,
        // and both v2 and v3 are in force from March onward. v_fee_schedule_overlaps reports that
        // afterwards; nothing prevented it, and effectiveAt() picks between them by version number
        // without any sign that it is choosing.
        //
        // One adjacent case was already refused, but for the wrong reason: publishing February
        // after March tripped the immutability trigger while trying to re-close an already-closed
        // window, and told the operator a version "may not be reopened or moved" — true, and
        // nothing to do with what they actually did, which was publish out of order.
        Optional<FeeSchedule> newest = schedules.newestVersion(code);
        if (newest.isPresent() && !effectiveFrom.isAfter(newest.get().getEffectiveFrom())) {
            throw new FeeScheduleException("Version " + newest.get().getVersion() + " of '" + code
                    + "' already takes effect at " + newest.get().getEffectiveFrom()
                    + "; a new version must start after it, not at or before it");
        }

        Optional<FeeSchedule> current = schedules.effectiveAt(code, effectiveFrom);

        FeeSchedule next = new FeeSchedule();
        next.setTenantCode(caller.tenantCode());
        next.setCode(code);
        next.setVersion(nextVersion(code));
        next.setEffectiveFrom(effectiveFrom);
        next.setBasisPoints(basisPoints);
        next.setFixedMinor(fixed == null ? 0L : fixed.amountMinor());
        next.setCurrency(fixed == null || fixed.amountMinor() == 0 ? null : fixed.currency());
        next.setNote(note);
        next.setCreatedBy(caller.actorId());
        next.persist();

        // Closing the open end is the one edit the immutability trigger allows, precisely because
        // it is the only one that does not change what was charged under the version it closes.
        current.ifPresent(previous -> {
            previous.setEffectiveTo(effectiveFrom);
            previous.persist();
        });
        return next;
    }

    private int nextVersion(String code) {
        return FeeSchedule.<FeeSchedule>find("code = ?1 and tenantCode = ?2 order by version desc", code, caller.tenantCode())
                .firstResultOptional()
                .map(latest -> latest.getVersion() + 1)
                .orElse(1);
    }
}
