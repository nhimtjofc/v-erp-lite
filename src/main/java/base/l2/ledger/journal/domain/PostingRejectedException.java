package base.l2.ledger.journal.domain;

import base.l0.system.api.HttpCodeException;

/** A posting the database refused: balance policy, credit limit, frozen account, bad reference. */
@HttpCodeException(status = 422, code = "LEDGER_POSTING_REJECTED")
public class PostingRejectedException extends RuntimeException {

    public PostingRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}

