package base.l1.platform.provider;

import base.l0.system.api.HttpCodeException;

/**
 * A webhook that did not verify. Deliberately a 400: a bad signature is permanent, and telling the
 * provider to retry it forever helps nobody.
 */
@HttpCodeException(status = 400, code = "WEBHOOK_INVALID")
public class WebhookVerificationException extends RuntimeException {

    public WebhookVerificationException(String message) {
        super(message);
    }
}

