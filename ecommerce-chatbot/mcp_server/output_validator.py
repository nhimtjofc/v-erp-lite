"""
Deterministic output validation for the AI agent's final reply (Step 5, Part A).

Per intern-ai-agent-design.md rule #5: use deterministic checks for known
high-risk failures and reject/retry a reply that
  - reveals protected instructions,
  - fabricates a record / status / price / PII, or
  - claims an action succeeded without application confirmation.

These are *behavioral* controls — a safety net, NOT a replacement for backend
authorization and the PII-stripping DTO (those are the structural controls). A
determined model can still phrase things around a regex; that is acceptable
because the structural layer already prevents unauthorized access. This module
only catches the obvious, high-signal failures cheaply and before delivery.

Independent of the FastAPI layer and the model, so it is fully unit-testable
with no server and no LLM. Wiring it into the /chat loop (validate -> one
no-tool retry -> safe fallback, before streaming) is Part B and lands with the
Step 3 tool loop.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Iterable, Sequence

SAFE_FALLBACK = "Xin lỗi, tôi chưa thể trả lời yêu cầu này. Anh/chị vui lòng thử lại nhé."


@dataclass(frozen=True)
class ValidationResult:
    ok: bool
    category: str = ""   # machine-readable failure slug ("" when ok)
    reason: str = ""     # short human-readable explanation (safe to log)

    def __bool__(self) -> bool:
        return self.ok


VALID = ValidationResult(True)


# ── Check 1: leaked technical error / traceback (server must never surface these) ──

_TECH_LEAK_MARKERS = (
    "traceback (most recent call last)",
    "traceback",
    "stack trace",
    "stacktrace",
)
# e.g.  File "server.py", line 70   |   order_ops_agent.py:73
_TECH_LEAK_RE = re.compile(
    r'(file\s+".*?\.py"|[\w./\\-]+\.py[:"\']|,\s*line\s+\d+|<pre>)',
    re.IGNORECASE,
)


# ── Check 2: leaked system / protected instructions ──

_GENERIC_INSTRUCTION_MARKERS = (
    "system prompt",
    "system instruction",
    "prompt hệ thống",
    "chỉ dẫn hệ thống",
    "câu lệnh hệ thống",
    "lưu ý bảo mật",          # our own secret-handling instruction header
    "you are a helpful",
    "my instructions are",
)


# ── Check 3: claim of a write action the read-only agent cannot have performed ──
# Targeted at FIRST-PERSON / "of yours" self-claims to avoid flagging legitimate
# status relays (e.g. "đơn hàng đã được giao" / "đơn đã thanh toán" are statuses,
# not the assistant claiming it acted).
_ACTION_CLAIM_RES = (
    # "tôi/chúng tôi đã hủy|hoàn tiền|đặt|cập nhật|xóa|tạo|thay đổi ..."
    re.compile(
        r"\b(tôi|chúng tôi)\s+(đã|vừa)\s+"
        r"(hủy|huỷ|hoàn\s*tiền|hoàn\s*trả|đặt|cập\s*nhật|xoá|xóa|tạo|thay\s*đổi|đổi)\b",
        re.IGNORECASE,
    ),
    # "đã hủy/hoàn tiền/... đơn hàng|yêu cầu ... của bạn/quý khách"
    re.compile(
        r"\bđã\s+(hủy|huỷ|hoàn\s*tiền|hoàn\s*trả|cập\s*nhật|xoá|xóa|tạo)\b"
        r"[^.]{0,40}\b(đơn\s*hàng|yêu\s*cầu|thông\s*tin)\b[^.]{0,20}\b(của\s+)?(bạn|quý\s*khách|anh|chị)\b",
        re.IGNORECASE,
    ),
    # English self-claims
    re.compile(
        r"\bI(?:'ve| have| just)\s+(cancel|refund|updat|creat|delet|plac|process|chang)\w*",
        re.IGNORECASE,
    ),
    re.compile(
        r"\bhas\s+been\s+(cancel|refund|updat|creat|delet|process)\w*",
        re.IGNORECASE,
    ),
)


# ── Check 4: PII in the reply that no tool result contained (fabricated/leaked) ──
# The OrderStatusDto strips PII, so tool results carry none; any PII in the reply
# is therefore fabricated or leaked and must be blocked.
_EMAIL_RE = re.compile(r"\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b")
# VN phone: leading 0 or +84, then 8-10 more digits (won't match money amounts,
# which don't start with 0 / +84).
_PHONE_RE = re.compile(r"(?<!\d)(?:\+84|0)\d{8,10}(?!\d)")


def _extract_pii(text: str) -> set[str]:
    return set(_EMAIL_RE.findall(text)) | set(_PHONE_RE.findall(text))


def validate_reply(
    reply: str,
    *,
    tool_results: Iterable[str] = (),
    protected_fragments: Sequence[str] = (),
) -> ValidationResult:
    """Validate a candidate final reply. Returns the first failure, else VALID.

    Args:
        reply: the assistant's candidate user-facing text.
        tool_results: raw tool outputs from this turn; PII present here is
            considered authorized data and allowed to appear in the reply.
        protected_fragments: exact instruction strings (e.g. the system prompt)
            that must never be echoed back; matched case-insensitively.
    """
    if reply is None or not reply.strip():
        return ValidationResult(False, "empty", "Empty reply")

    low = reply.lower()

    # 1. technical/error leak
    if any(m in low for m in _TECH_LEAK_MARKERS) or _TECH_LEAK_RE.search(reply):
        return ValidationResult(False, "tech_leak", "Reply leaks a technical error/traceback")

    # 2. instruction leak
    for frag in protected_fragments:
        f = frag.strip().lower()
        if f and f in low:
            return ValidationResult(False, "instruction_leak", "Reply echoes protected instructions")
    if any(m in low for m in _GENERIC_INSTRUCTION_MARKERS):
        return ValidationResult(False, "instruction_leak", "Reply reveals system-instruction markers")

    # 3. unconfirmed write-action success claim
    for rx in _ACTION_CLAIM_RES:
        if rx.search(reply):
            return ValidationResult(False, "action_claim",
                                    "Reply claims a write action the read-only agent cannot confirm")

    # 4. fabricated / leaked PII
    reply_pii = _extract_pii(reply)
    if reply_pii:
        allowed = set()
        joined = "\n".join(tool_results)
        for item in reply_pii:
            if item in joined:
                allowed.add(item)
        if reply_pii - allowed:
            return ValidationResult(False, "pii", "Reply contains PII absent from tool results")

    return VALID


def is_valid(reply: str, **kwargs) -> bool:
    return bool(validate_reply(reply, **kwargs))
