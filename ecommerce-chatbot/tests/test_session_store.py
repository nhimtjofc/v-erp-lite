"""
Step 4 Part A tests: server-owned, append-only, owner-scoped session store.
No server, no LLM — pure store logic.

Run from ecommerce-chatbot/:
    python -m pytest tests/test_session_store.py -v
"""
import os
import sys
import threading

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from mcp_server.request_context import RequestContext
from mcp_server.session_store import (
    InMemorySessionStore,
    SessionAccessDenied,
    SessionBusy,
    SessionNotFound,
    SessionStore,
    SessionTooLarge,
)


def ctx(user_id="user-a", tenant="tenant-1", email="a@x.com", roles=("customer",)):
    return RequestContext(user_id=user_id, email=email, tenant=tenant, roles=roles)


@pytest.fixture
def store():
    return InMemorySessionStore(max_messages=5)


# ── basic lifecycle ──

def test_create_returns_unguessable_id_and_empty_history(store):
    c = ctx()
    sid = store.create(c)
    assert isinstance(sid, str) and len(sid) >= 32
    assert store.load(sid, c) == []

def test_create_ids_are_unique(store):
    c = ctx()
    assert store.create(c) != store.create(c)

def test_in_memory_satisfies_protocol():
    assert isinstance(InMemorySessionStore(), SessionStore)


# ── append-only ──

def test_append_then_load_preserves_order(store):
    c = ctx()
    sid = store.create(c)
    store.append_turn(sid, c, [{"role": "user", "content": "hi"}])
    store.append_turn(sid, c, [{"role": "assistant", "content": "hello"}])
    assert store.load(sid, c) == [
        {"role": "user", "content": "hi"},
        {"role": "assistant", "content": "hello"},
    ]

def test_load_returns_copy_not_internal_list(store):
    c = ctx()
    sid = store.create(c)
    store.append_turn(sid, c, [{"role": "user", "content": "hi"}])
    hist = store.load(sid, c)
    hist.append({"role": "user", "content": "injected"})
    assert len(store.load(sid, c)) == 1  # internal state untouched

def test_append_empty_is_noop(store):
    c = ctx()
    sid = store.create(c)
    store.append_turn(sid, c, [])
    assert store.load(sid, c) == []


# ── owner scoping ──

def test_other_user_cannot_load(store):
    owner = ctx(user_id="user-a")
    sid = store.create(owner)
    store.append_turn(sid, owner, [{"role": "user", "content": "secret"}])
    with pytest.raises(SessionAccessDenied):
        store.load(sid, ctx(user_id="user-b"))

def test_other_tenant_cannot_load(store):
    owner = ctx(tenant="tenant-1")
    sid = store.create(owner)
    with pytest.raises(SessionAccessDenied):
        store.load(sid, ctx(tenant="tenant-2"))

def test_other_user_cannot_append(store):
    owner = ctx(user_id="user-a")
    sid = store.create(owner)
    with pytest.raises(SessionAccessDenied):
        store.append_turn(sid, ctx(user_id="user-b"), [{"role": "user", "content": "x"}])

def test_unknown_session_raises_not_found(store):
    with pytest.raises(SessionNotFound):
        store.load("does-not-exist", ctx())


# ── size bound + atomicity ──

def test_append_exceeding_bound_rejected(store):  # max_messages=5
    c = ctx()
    sid = store.create(c)
    store.append_turn(sid, c, [{"i": i} for i in range(5)])
    with pytest.raises(SessionTooLarge):
        store.append_turn(sid, c, [{"i": 5}])

def test_rejected_append_leaves_no_partial_history(store):  # max_messages=5
    c = ctx()
    sid = store.create(c)
    store.append_turn(sid, c, [{"i": 0}, {"i": 1}])
    # This 4-message append would reach 6 > 5 -> must be all-or-nothing.
    with pytest.raises(SessionTooLarge):
        store.append_turn(sid, c, [{"i": 2}, {"i": 3}, {"i": 4}, {"i": 5}])
    assert store.load(sid, c) == [{"i": 0}, {"i": 1}]  # unchanged


# ── concurrency ──

def test_concurrent_turn_rejected(store):
    c = ctx()
    sid = store.create(c)
    with store.turn(sid, c):
        with pytest.raises(SessionBusy):
            with store.turn(sid, c):
                pass

def test_turn_lock_released_after_block(store):
    c = ctx()
    sid = store.create(c)
    with store.turn(sid, c):
        pass
    # Should be free again now.
    with store.turn(sid, c):
        pass

def test_turn_lock_released_even_on_exception(store):
    c = ctx()
    sid = store.create(c)
    with pytest.raises(RuntimeError):
        with store.turn(sid, c):
            raise RuntimeError("turn blew up")
    with store.turn(sid, c):  # not stuck busy
        pass

def test_non_owner_cannot_probe_busy_state(store):
    """A non-owner gets access-denied, never SessionBusy — no existence oracle."""
    owner = ctx(user_id="user-a")
    sid = store.create(owner)
    with store.turn(sid, owner):
        with pytest.raises(SessionAccessDenied):
            with store.turn(sid, ctx(user_id="user-b")):
                pass

def test_different_sessions_dont_block_each_other(store):
    c = ctx()
    sid1, sid2 = store.create(c), store.create(c)
    with store.turn(sid1, c):
        with store.turn(sid2, c):  # independent session, no block
            pass

def test_thread_safety_single_winner_under_race(store):
    """Two threads racing the same turn: exactly one wins, one gets SessionBusy."""
    c = ctx()
    sid = store.create(c)
    barrier = threading.Barrier(2)
    results = []

    def worker():
        barrier.wait()
        try:
            with store.turn(sid, c):
                results.append("ok")
                # Hold the turn briefly so the other thread contends.
                threading.Event().wait(0.05)
        except SessionBusy:
            results.append("busy")

    t1, t2 = threading.Thread(target=worker), threading.Thread(target=worker)
    t1.start(); t2.start(); t1.join(); t2.join()
    assert sorted(results) == ["busy", "ok"]
