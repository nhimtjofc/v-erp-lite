import os
import sys
if sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')
from agents.router_agent import RouterAgent

print("=== KIỂM TRA BỘ NÃO GEMINI AI ===")
agent = RouterAgent()

print("\n[User]: Cho tôi hỏi đơn hàng mã #123 của tôi đã được gửi đi chưa?")
intent1 = agent.route_query("Cho tôi hỏi đơn hàng mã #123 của tôi đã được gửi đi chưa?")
print(f"[AI Quyết định gọi Agent]: >>> {intent1} <<<")

print("\n[User]: Tôi muốn tìm một đôi giày chạy bộ thoải mái màu đỏ")
intent2 = agent.route_query("Tôi muốn tìm một đôi giày chạy bộ thoải mái màu đỏ")
print(f"[AI Quyết định gọi Agent]: >>> {intent2} <<<")
