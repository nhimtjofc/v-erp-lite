package base.l2.ledger.journal.domain;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 422, code = "LEDGER_UNBALANCED")
public class UnbalancedEntryException extends RuntimeException {

    public UnbalancedEntryException(String message) {
        super(message);
    }
}

