package base.l2.settlement.dispute.domain;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 400, code = "DISPUTE_REJECTED")
public class DisputeRejectedException extends RuntimeException {

    public DisputeRejectedException(String message) {
        super(message);
    }
}

