package base.l2.ledger.reporting.application;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 400, code = "INVALID_CURSOR")
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("'" + cursor + "' is not a cursor this service issued; pass back the nextCursor "
                + "from the previous page unmodified");
    }
}

