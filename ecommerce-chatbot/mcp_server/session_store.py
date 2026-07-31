"""
Server-owned, append-only conversation sessions for the AI agent (Step 4, Part A).

Design goals (intern-ai-agent-design.md rule #4 + acceptance criteria):
  - History lives on the server; the client sends only a message + optional
    session_id, never the history itself.
  - Sessions are owner-scoped: a session belongs to exactly one (tenant, user)
    derived from the authenticated RequestContext (Step 1). A different caller
    can never read or append to it.
  - Append-only: a completed turn's messages are appended; nothing rewrites or
    trims the prefix on every turn (that would break prefix caching + tool
    pairing). Session size is bounded instead.
  - Concurrent turns on the same session are *rejected*, not interleaved.
  - A failed turn persists no partial history (append is all-or-nothing).

The store treats each message as an opaque provider-shaped dict — it never
inspects or rebuilds message contents. That keeps raw assistant/tool messages
intact for round-tripping once the bounded tool loop (Step 3) lands (Part B).

This module is intentionally decoupled from the FastAPI layer and from the model
so it can be unit-tested with no server and no LLM. The `SessionStore` Protocol
lets a MySQL/Redis-backed implementation drop in later without touching callers.
"""
from __future__ import annotations

import secrets
import threading
import time
from contextlib import contextmanager
from dataclasses import dataclass, field
from typing import Iterable, Iterator, List, Protocol, runtime_checkable

from mcp_server.request_context import RequestContext

# A message is an opaque, provider-shaped mapping (e.g. {"role": "...", ...}).
Message = dict


# ── Errors (callers map these to safe HTTP responses; see note on leakage) ──

class SessionError(Exception):
    """Base class for all session-store failures."""


class SessionNotFound(SessionError):
    """No session exists for the given id."""


class SessionAccessDenied(SessionError):
    """The caller is not the owner of the session.

    NOTE: the HTTP layer MUST return an identical generic response for both
    SessionNotFound and SessionAccessDenied, so a non-owner cannot use error
    differences to probe which session ids exist.
    """


class SessionBusy(SessionError):
    """A turn is already in progress for this session (concurrent turn)."""


class SessionTooLarge(SessionError):
    """Appending would exceed the session's bounded message count."""


def _owner_key(ctx: RequestContext) -> str:
    """Stable owner identity: (tenant, user). Uses a unit-separator so the two
    fields can never collide across a boundary."""
    return f"{ctx.tenant}\x1f{ctx.user_id}"


@dataclass
class _SessionRecord:
    session_id: str
    owner: str
    messages: List[Message] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)


@runtime_checkable
class SessionStore(Protocol):
    """Interface callers depend on. In-memory now; MySQL/Redis later, no caller change."""

    def create(self, ctx: RequestContext) -> str:
        """Create a new empty session owned by the caller; return its id."""

    def load(self, session_id: str, ctx: RequestContext) -> List[Message]:
        """Return a copy of the session history; enforce ownership."""

    def append_turn(self, session_id: str, ctx: RequestContext,
                    messages: Iterable[Message]) -> None:
        """Append a completed turn's messages atomically; enforce ownership + size bound."""

    def turn(self, session_id: str, ctx: RequestContext):
        """Context manager that guards one turn: verifies ownership and rejects a
        concurrent turn on the same session for its whole duration."""


class InMemorySessionStore:
    """Process-local, thread-safe implementation.

    Caveats (acceptable for the MVP; swap the implementation to persist):
      - History is lost on restart.
      - Not shared across processes — run a single uvicorn worker.
    """

    def __init__(self, max_messages: int = 100):
        if max_messages < 1:
            raise ValueError("max_messages must be >= 1")
        self._max_messages = max_messages
        self._sessions: dict[str, _SessionRecord] = {}
        self._active: set[str] = set()          # session_ids with a turn in flight
        self._lock = threading.RLock()          # guards _sessions + _active

    # ── internal ──

    def _get_owned(self, session_id: str, ctx: RequestContext) -> _SessionRecord:
        """Fetch a session, enforcing existence + ownership. Caller holds _lock."""
        rec = self._sessions.get(session_id)
        if rec is None:
            raise SessionNotFound("No such session")
        if rec.owner != _owner_key(ctx):
            raise SessionAccessDenied("Session belongs to another caller")
        return rec

    # ── public API ──

    def create(self, ctx: RequestContext) -> str:
        # token_urlsafe(32) -> 256 bits of entropy: unguessable, non-enumerable.
        session_id = secrets.token_urlsafe(32)
        with self._lock:
            self._sessions[session_id] = _SessionRecord(
                session_id=session_id, owner=_owner_key(ctx)
            )
        return session_id

    def load(self, session_id: str, ctx: RequestContext) -> List[Message]:
        with self._lock:
            rec = self._get_owned(session_id, ctx)
            # Return a shallow copy so callers can't mutate stored history in place.
            return list(rec.messages)

    def append_turn(self, session_id: str, ctx: RequestContext,
                    messages: Iterable[Message]) -> None:
        new = list(messages)
        with self._lock:
            rec = self._get_owned(session_id, ctx)
            if not new:
                return  # nothing to append; no-op keeps callers simple
            # Check the bound BEFORE mutating so a rejected append leaves the
            # session exactly as it was (no partial history).
            if len(rec.messages) + len(new) > self._max_messages:
                raise SessionTooLarge(
                    f"Session would exceed {self._max_messages} messages"
                )
            rec.messages.extend(new)
            rec.updated_at = time.time()

    @contextmanager
    def turn(self, session_id: str, ctx: RequestContext) -> Iterator[None]:
        """Guard one turn. Verifies ownership up front and marks the session busy
        for the whole `with` block; a second concurrent turn raises SessionBusy.

        Usage:
            with store.turn(session_id, ctx):
                history = store.load(session_id, ctx)
                ...call model (this is where an await/blocking call happens)...
                store.append_turn(session_id, ctx, new_messages)  # only on success
        """
        with self._lock:
            # Ownership is checked before we reveal busy/free state, so a
            # non-owner never learns whether a turn is in progress.
            self._get_owned(session_id, ctx)
            if session_id in self._active:
                raise SessionBusy("A turn is already in progress for this session")
            self._active.add(session_id)
        try:
            yield
        finally:
            with self._lock:
                self._active.discard(session_id)


# Default process-wide store for the MVP. Swap this construction (e.g. for a
# MySQLSessionStore) without changing any caller that depends on SessionStore.
default_store: SessionStore = InMemorySessionStore()
