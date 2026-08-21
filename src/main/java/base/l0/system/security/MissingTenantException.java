package base.l0.system.security;

import base.l0.system.api.HttpCodeException;

@HttpCodeException(status = 401, code = "TENANT_MISSING")
public class MissingTenantException extends RuntimeException {

    public MissingTenantException(String message) {
        super(message);
    }
}

