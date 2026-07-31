"""
Step 1 denial tests: identity/authentication gate, no LLM involved.

Run from the ecommerce-chatbot/ directory:
    python -m pytest tests/test_request_context.py -v

The signed-token tests mock the Keycloak JWKS so they run fully offline.
"""
import datetime
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from mcp_server import request_context as rc
from mcp_server.request_context import AuthError, verify_bearer, ISSUER


# ── Pure claim-resolution logic (no crypto) ──

def test_tenant_prefers_organization_array():
    assert rc._tenant_from_claims({"organization": ["v-erp-app"], "azp": "x"}) == "v-erp-app"

def test_tenant_falls_back_to_tenant_code_then_azp_then_default():
    assert rc._tenant_from_claims({"tenant_code": "acme"}) == "acme"
    assert rc._tenant_from_claims({"azp": "v-erp-app"}) == "v-erp-app"
    assert rc._tenant_from_claims({}) == "default"

def test_roles_extracted_from_realm_access():
    assert rc._roles_from_claims({"realm_access": {"roles": ["customer", "user"]}}) == ("customer", "user")
    assert rc._roles_from_claims({}) == ()


# ── Header shape denials (reject before any crypto) ──

def test_missing_header_denied():
    with pytest.raises(AuthError):
        verify_bearer(None)

def test_malformed_header_denied():
    for bad in ["", "Bearer", "Bearer ", "Token abc", "abc"]:
        with pytest.raises(AuthError):
            verify_bearer(bad)


# ── Signed-token path (mock JWKS) ──

@pytest.fixture
def rsa_keypair():
    crypto = pytest.importorskip("cryptography")
    from cryptography.hazmat.primitives.asymmetric import rsa
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return key

@pytest.fixture
def patch_jwks(monkeypatch, rsa_keypair):
    class _FakeKey:
        key = rsa_keypair.public_key()
    class _FakeClient:
        def get_signing_key_from_jwt(self, token):
            return _FakeKey()
    monkeypatch.setattr(rc, "_jwks_client", lambda: _FakeClient())
    return rsa_keypair

def _make_token(private_key, claims):
    jwt = pytest.importorskip("jwt")
    return jwt.encode(claims, private_key, algorithm="RS256")

def _base_claims(**over):
    now = datetime.datetime.now(datetime.timezone.utc)
    claims = {
        "sub": "11111111-1111-1111-1111-111111111111",
        "email": "buyer@example.com",
        "iss": ISSUER,
        "exp": now + datetime.timedelta(minutes=5),
        "iat": now,
        "organization": ["v-erp-app"],
        "realm_access": {"roles": ["customer"]},
    }
    claims.update(over)
    return claims

def test_valid_token_builds_context(patch_jwks):
    token = _make_token(patch_jwks, _base_claims())
    ctx = verify_bearer(f"Bearer {token}")
    assert ctx.user_id == "11111111-1111-1111-1111-111111111111"
    assert ctx.email == "buyer@example.com"
    assert ctx.tenant == "v-erp-app"
    assert ctx.roles == ("customer",)
    assert ctx.access_token == token

def test_expired_token_denied(patch_jwks):
    now = datetime.datetime.now(datetime.timezone.utc)
    token = _make_token(patch_jwks, _base_claims(
        exp=now - datetime.timedelta(minutes=1),
        iat=now - datetime.timedelta(minutes=10),
    ))
    with pytest.raises(AuthError):
        verify_bearer(f"Bearer {token}")

def test_wrong_issuer_denied(patch_jwks):
    token = _make_token(patch_jwks, _base_claims(iss="http://evil/realms/other"))
    with pytest.raises(AuthError):
        verify_bearer(f"Bearer {token}")

def test_token_without_subject_denied(patch_jwks):
    claims = _base_claims()
    del claims["sub"]
    token = _make_token(patch_jwks, claims)
    with pytest.raises(AuthError):
        verify_bearer(f"Bearer {token}")

def test_access_token_not_in_repr(patch_jwks):
    token = _make_token(patch_jwks, _base_claims())
    ctx = verify_bearer(f"Bearer {token}")
    assert token not in repr(ctx)
