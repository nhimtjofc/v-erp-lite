"""
Privacy-safe operational telemetry for the AI Chatbot.

Per intern-ai-agent-design.md acceptance criteria and component responsibilities:
- Ghi nhận: counts, latency, tool usage counts, turn outcome status.
- MUST NOT DO: Store prompts, user messages, PII, tool arguments, or tool results.
"""
from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from typing import Dict

logger = logging.getLogger("ai_telemetry")
if not logger.handlers:
    handler = logging.StreamHandler()
    formatter = logging.Formatter("[TELEMETRY] %(asctime)s - %(message)s")
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)


@dataclass
class TurnTelemetry:
    session_id: str
    tenant: str
    user_id: str
    start_time: float = field(default_factory=time.time)
    duration_ms: float = 0.0
    status: str = "pending"  # success, validation_retry, validation_failed, error, busy
    tool_calls: Dict[str, int] = field(default_factory=dict)
    retry_count: int = 0
    error_type: str = ""

    def record_tool_call(self, tool_name: str) -> None:
        self.tool_calls[tool_name] = self.tool_calls.get(tool_name, 0) + 1

    def finish(self, status: str, error_type: str = "") -> None:
        self.duration_ms = round((time.time() - self.start_time) * 1000, 2)
        self.status = status
        self.error_type = error_type
        # Log operational metrics only — NO prompt, NO args, NO PII
        logger.info(
            f"turn_finished: session={self.session_id} tenant={self.tenant} "
            f"duration_ms={self.duration_ms} status={self.status} "
            f"tools={self.tool_calls} retries={self.retry_count} "
            f"error={self.error_type}"
        )
