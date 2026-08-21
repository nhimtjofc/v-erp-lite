package base.l2.settlement.intent.domain;

import base.l0.system.api.HttpCodeException;

/** A capture whose numbers do not describe a transaction that could have happened. */
@HttpCodeException(status = 400, code = "CAPTURE_REJECTED")
public class CaptureRejectedException extends RuntimeException {

    public CaptureRejectedException(String message) {
        super(message);
    }
}

