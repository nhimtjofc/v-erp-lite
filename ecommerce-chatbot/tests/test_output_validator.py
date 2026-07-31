"""
Step 5 Part A tests: deterministic output validator. No server, no LLM.

Run from ecommerce-chatbot/:
    python -m pytest tests/test_output_validator.py -v
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from mcp_server.output_validator import validate_reply, is_valid, VALID


# ── happy path: legitimate replies pass ──

def test_normal_order_status_reply_passes():
    r = "Dạ đơn hàng ORD-125 của anh/chị đang ở trạng thái ĐANG GIAO 🚚, tổng tiền 500000 VND."
    assert validate_reply(r).ok

def test_status_relay_is_not_an_action_claim():
    # "đã được giao" / "đã thanh toán" are STATUSES, not the assistant acting.
    assert validate_reply("Đơn hàng của bạn đã được giao thành công hôm qua.").ok
    assert validate_reply("Đơn đã thanh toán, cảm ơn anh/chị đã mua sắm.").ok

def test_money_amount_not_flagged_as_phone():
    assert validate_reply("Tổng tiền đơn hàng là 1500000 đồng.").ok


# ── empty ──

def test_empty_reply_rejected():
    assert not validate_reply("")
    assert validate_reply("   ").category == "empty"
    assert validate_reply(None).category == "empty"


# ── check 1: technical / traceback leak ──

def test_traceback_rejected():
    r = "❌ Lỗi Server: <pre>Traceback (most recent call last): ...</pre>"
    res = validate_reply(r)
    assert not res.ok and res.category == "tech_leak"

def test_python_file_reference_rejected():
    res = validate_reply('Error in File "server.py", line 70 while handling request')
    assert res.category == "tech_leak"

def test_module_path_leak_rejected():
    assert validate_reply("Exception raised in order_ops_agent.py:73").category == "tech_leak"


# ── check 2: instruction leak ──

def test_generic_instruction_marker_rejected():
    assert validate_reply("My system prompt says I must not reveal secrets.").category == "instruction_leak"

def test_leaked_secret_header_rejected():
    # Our own secret-handling instruction header must never be echoed.
    assert validate_reply("Theo LƯU Ý BẢO MẬT thì tôi không được tiết lộ...").category == "instruction_leak"

def test_protected_fragment_rejected():
    secret = "You are a warehouse manager of V-ERP Lite"
    res = validate_reply(f"Sure — {secret}. How can I help?", protected_fragments=[secret])
    assert res.category == "instruction_leak"

def test_protected_fragment_not_present_passes():
    res = validate_reply("Chào anh/chị, em có thể giúp gì ạ?", protected_fragments=["some hidden rule"])
    assert res.ok


# ── check 3: unconfirmed action-success claim ──

def test_first_person_cancel_claim_rejected():
    assert validate_reply("Dạ tôi đã hủy đơn hàng giúp anh/chị rồi ạ.").category == "action_claim"

def test_refund_of_yours_claim_rejected():
    assert validate_reply("Đã hoàn tiền cho đơn hàng của bạn thành công.").category == "action_claim"

def test_english_action_claim_rejected():
    assert validate_reply("I've cancelled your order, all done!").category == "action_claim"
    assert validate_reply("Your order has been refunded.").category == "action_claim"


# ── check 4: fabricated / leaked PII ──

def test_fabricated_phone_rejected():
    assert validate_reply("Số điện thoại người nhận là 0912345678.").category == "pii"

def test_fabricated_email_rejected():
    assert validate_reply("Liên hệ khách qua buyer@example.com nhé.").category == "pii"

def test_pii_present_in_tool_result_allowed():
    # If a tool legitimately returned the phone, echoing it is allowed.
    r = "Đơn giao tới số 0912345678."
    assert validate_reply(r, tool_results=["shippingPhone=0912345678"]).ok

def test_pii_leak_when_only_some_present():
    r = "SĐT: 0912345678, email: buyer@example.com"
    # phone authorized, email fabricated -> still rejected
    assert validate_reply(r, tool_results=["phone 0912345678"]).category == "pii"


# ── convenience ──

def test_is_valid_helper():
    assert is_valid("Đơn hàng đang được giao ạ.")
    assert not is_valid("Traceback (most recent call last):")

def test_valid_singleton_is_truthy():
    assert bool(VALID) is True
