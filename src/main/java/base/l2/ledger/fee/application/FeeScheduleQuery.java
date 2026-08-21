package base.l2.ledger.fee.application;

import base.l2.ledger.fee.domain.FeeSchedule;
import base.l2.ledger.fee.domain.FeeScheduleException;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;

/** Which version of a fee arrangement was in force at a given moment. */
@ApplicationScoped
public class FeeScheduleQuery {

    /**
     * The version covering {@code at}, if any.
     *
     * <p>Half-open window: {@code effective_from <= at < effective_to}. The boundary matters more
     * than it looks — a rate change at midnight must price the last capture of the old day under the
     * old version and the first of the new day under the new one, and a closed interval on both ends
     * makes the instant of changeover belong to two versions at once.
     */
    public Optional<FeeSchedule> effectiveAt(String code, Instant at) {
        return FeeSchedule.<FeeSchedule>find(
                        "code = ?1 and effectiveFrom <= ?2 and (effectiveTo is null or effectiveTo > ?2) "
                                + "order by version desc", code, at)
                .firstResultOptional();
    }

    /** The latest version published for a code, whatever window it covers. */
    public Optional<FeeSchedule> newestVersion(String code) {
        return FeeSchedule.<FeeSchedule>find("code = ?1 order by version desc", code)
                .firstResultOptional();
    }

    /**
     * The version covering {@code at}, or a refusal.
     *
     * <p>No fallback to "the latest" and no default rate. A capture quoting a schedule that was not
     * in force is a caller and a service disagreeing about what was charged, and guessing at the
     * answer would put a number on a ledger entry that nothing can reproduce.
     */
    public FeeSchedule require(String code, Instant at) {
        return effectiveAt(code, at).orElseThrow(() -> new FeeScheduleException(
                "No version of fee schedule '" + code + "' was in force at " + at));
    }
}

