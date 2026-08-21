package base.l2.ledger.journal.domain;

import base.l0.system.api.HttpCodeException;

/**
 * The idempotency key has already been posted. Callers replaying a webhook should treat this as
 * success, not failure — the work was already done.
 */
@HttpCodeException(status = 409, code = "LEDGER_DUPLICATE_ENTRY")
public class DuplicateEntryException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateEntryException(String idempotencyKey) {
        super("Entry already posted for idempotency key " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}

