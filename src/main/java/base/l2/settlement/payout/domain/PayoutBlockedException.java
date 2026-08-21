package base.l2.settlement.payout.domain;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 409, code = "PAYOUT_BLOCKED")
public class PayoutBlockedException extends RuntimeException {

    public PayoutBlockedException(String message) {
        super(message);
    }
}

