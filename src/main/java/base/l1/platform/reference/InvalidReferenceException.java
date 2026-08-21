package base.l1.platform.reference;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 400, code = "INVALID_PAYMENT_REFERENCE")
public class InvalidReferenceException extends RuntimeException {

    public InvalidReferenceException(String message) {
        super(message);
    }
}

