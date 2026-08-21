package base.l2.recon.bank.domain;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 400, code = "BANK_TRANSACTION_REJECTED")
public class BankTransactionRejectedException extends RuntimeException {

    public BankTransactionRejectedException(String message) {
        super(message);
    }
}

