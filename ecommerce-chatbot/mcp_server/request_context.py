"""
Request-scoped caller identity for the AI agent.

Mirrors the backend's ``CallerContext`` (src/main/java/base/security/CallerContext.java):
identity is derived from the authenticated OIDC (Keycloak) access token — never from
anything the model or the client body can influence. The model sees only the message;
who the caller is stays here, outside the model.

Verification matches Quarkus quarkus-oidc defaults for this realm:
  - signature checked against the realm JWKS,
  - issuer + expiry enforced,
  - audience NOT enforced (Quarkus has no `quarkus.oidc.token.audience` configured).
"""
import os
import contextvars
from dataclasses import dataclass, field
from functools import lru_cache

try:
    import jwt
    from jwt import PyJWKClient
except ImportError:
    jwt = None
    PyJWKClient = None


# ── Keycloak config (defaults match src/main/resources/application.properties) ──
KEYCLOAK_URL = os.environ.get("KEYCLOAK_URL", "http://localhost:8181")
KEYCLOAK_REALM = os.environ.get("KEYCLOAK_REALM", "v-erp-lite")

ISSUER = f"{KEYCLOAK_URL}/realms/{KEYCLOAK_REALM}"
JWKS_URI = f"{ISSUER}/protocol/openid-connect/certs"


@dataclass(frozen=True)
class RequestContext:
    """Trusted identity carried to tools. Must NEVER appear in a tool schema
    or the shared prompt prefix — see intern-ai-agent-design.md rule #1 & #3."""
    user_id: str          # Keycloak subject (sub)
    email: str            # email claim
    tenant: str           # organization[0] -> tenant_code -> azp -> "default"
    roles: tuple = field(default_factory=tuple)
    # Raw bearer token, forwarded to the backend so per-user authorization is
    # enforced there (Step 2). Kept out of __repr__ to avoid leaking into logs.
    access_token: str = field(default="", repr=False)


class AuthError(Exception):
    """Raised when a bearer token is missing, malformed, or invalid."""


@lru_cache(maxsize=1)
def _jwks_client() -> PyJWKClient:
    # PyJWKClient caches fetched signing keys internally.
    return PyJWKClient(JWKS_URI)


def _tenant_from_claims(claims: dict) -> str:
    """Resolution order mirrors CallerContext.tenantCode()."""
    org = claims.get("organization")
    if isinstance(org, list) and org:
        return org[0]
    if isinstance(org, str) and org:
        return org
    for key in ("tenant_code", "azp"):
        val = claims.get(key)
        if val:
            return val
    return "default"


def _roles_from_claims(claims: dict) -> tuple:
    """realm_access/roles — matches quarkus.oidc.roles.role-claim-path."""
    realm_access = claims.get("realm_access") or {}
    roles = realm_access.get("roles") or []
    return tuple(roles) if isinstance(roles, list) else ()


def verify_bearer(authorization_header: str | None) -> RequestContext:
    """Verify an ``Authorization: Bearer <jwt>`` header and build a RequestContext.

    Raises AuthError on any missing/malformed/invalid token — the caller maps
    this to HTTP 401.
    """
    if not authorization_header:
        print("[AUTH ERROR] Missing Authorization header in request")
        raise AuthError("Missing Authorization header")

    parts = authorization_header.split(None, 1)
    if len(parts) != 2 or parts[0].lower() != "bearer" or not parts[1].strip():
        print(f"[AUTH ERROR] Malformed Authorization header: {authorization_header[:30]}...")
        raise AuthError("Malformed Authorization header; expected 'Bearer <token>'")
    token = parts[1].strip()

    try:
        signing_key = _jwks_client().get_signing_key_from_jwt(token)
        claims = jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            issuer=ISSUER,
            options={"verify_aud": False},  # match Quarkus (no audience configured)
        )
    except AuthError:
        raise
    except Exception as e:
        print(f"[AUTH ERROR] Token verification failed: {type(e).__name__}: {e}")
        # Do not leak crypto/internal detail to the caller or the model.
        raise AuthError("Invalid or expired token") from e

    sub = claims.get("sub")
    email = claims.get("email")
    if not sub:
        print("[AUTH ERROR] Token has no sub claim")
        raise AuthError("Token has no subject (sub) claim")

    return RequestContext(
        user_id=sub,
        email=email or "",
        tenant=_tenant_from_claims(claims),
        roles=_roles_from_claims(claims),
        access_token=token,
    )


# Thread/coroutine-local context variable to propagate trusted RequestContext to tool functions
current_request_context: contextvars.ContextVar[RequestContext] = contextvars.ContextVar("current_request_context")

