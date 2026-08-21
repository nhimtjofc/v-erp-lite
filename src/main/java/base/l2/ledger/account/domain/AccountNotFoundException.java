package base.l2.ledger.account.domain;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 404, code = "LEDGER_ACCOUNT_NOT_FOUND")
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String code, String currency) {
        super("No ledger account " + code + " in " + currency);
    }
}

