package base.l1.platform.provider;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 400, code = "PROVIDER_UNKNOWN")
public class UnknownProviderException extends RuntimeException {

    public UnknownProviderException(String id, java.util.Collection<String> known) {
        super("No payment provider '" + id + "' is configured. Known providers: " + known);
    }
}

