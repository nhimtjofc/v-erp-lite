package base.l2.recon.domain;

/**
 * One failed invariant. {@code severity} decides whether the account is frozen or merely reported:
 * a book that does not balance is not a metric, it is a stop-work condition.
 */
public record InvariantBreach(
        String invariant,
        Severity severity,
        String tenantCode,
        String subject,
        String detail,
        long magnitudeMinor
) {
    public enum Severity {
        /** The books are wrong. Freeze and escalate. */
        CRITICAL,
        /** Something is drifting and will become wrong. Alert. */
        WARNING
    }
}

