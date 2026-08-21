package base.l2.ledger.fee.domain;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 400, code = "FEE_SCHEDULE_INVALID")
public class FeeScheduleException extends RuntimeException {

    public FeeScheduleException(String message) {
        super(message);
    }
}

