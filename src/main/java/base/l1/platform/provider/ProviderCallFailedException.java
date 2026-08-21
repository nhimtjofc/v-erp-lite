package base.l1.platform.provider;

import base.l0.system.api.HttpCodeException;

/**
 * An outbound call to a provider that did not succeed.
 *
 * <p>502 rather than 500: the failure is upstream, and a caller deciding whether to retry needs to
 * know the difference between "we are broken" and "they are". The provider's own status and body
 * are carried through because a payment provider's rejection reason is usually the whole answer.
 */
@HttpCodeException(status = 502, code = "PROVIDER_CALL_FAILED")
public class ProviderCallFailedException extends RuntimeException {

    public ProviderCallFailedException(String provider, String path, int status, String detail) {
        super("Provider '" + provider + "' call to " + path
                + (status > 0 ? " returned " + status : " failed") + ": " + detail);
    }
}

