package base.l2.ledger.allocation.domain;

import base.l0.system.api.HttpCodeException;

/** An allocation that does not describe two lines settling each other. */
@HttpCodeException(status = 400, code = "ALLOCATION_REJECTED")
public class AllocationRejectedException extends RuntimeException {

    public AllocationRejectedException(String message) {
        super(message);
    }
}

