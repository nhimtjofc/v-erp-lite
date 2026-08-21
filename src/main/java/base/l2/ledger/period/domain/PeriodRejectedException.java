package base.l2.ledger.period.domain;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 400, code = "PERIOD_REJECTED")
public class PeriodRejectedException extends RuntimeException {

    public PeriodRejectedException(String message) {
        super(message);
    }
}

