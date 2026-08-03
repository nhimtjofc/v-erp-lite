"""
Unit tests for Bounded Tool Loop, Argument Guard, list_customer_orders tool, and Telemetry.
Runs offline with standard library unittest.
"""
import os
import sys
import unittest
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from mcp_server.request_context import RequestContext, current_request_context
from mcp_server.telemetry import TurnTelemetry
from mcp_server.output_validator import validate_reply, SAFE_FALLBACK


class TestAgentLoopAndTools(unittest.TestCase):

    def setUp(self):
        self.sample_ctx = RequestContext(
            user_id="usr-12345",
            email="customer@example.com",
            tenant="v-erp-app",
            roles=("customer",),
            access_token="mock_bearer_token"
        )

    def test_argument_guard_blocks_identity_parameters(self):
        """Rule #1 & #3: Schemas and runtime calls must reject identity parameters."""
        from agents.unified_agent import UnifiedAgent
        
        with patch.dict(os.environ, {"GEMINI_API_KEY": "fake_key"}):
            agent = UnifiedAgent()
            
            # Forbidden terms
            self.assertFalse(agent._validate_args({"user_id": "123"}))
            self.assertFalse(agent._validate_args({"tenant": "v-erp"}))
            self.assertFalse(agent._validate_args({"customerEmail": "a@b.com"}))
            self.assertFalse(agent._validate_args({"account_id": "999"}))
            self.assertFalse(agent._validate_args({"query": "customer_data"}))
            
            # Allowed semantic terms
            self.assertTrue(agent._validate_args({"order_id": "ORD-123"}))
            self.assertTrue(agent._validate_args({"status_filter": "SHIPPING"}))
            self.assertTrue(agent._validate_args({"query": "áo thun nam"}))
            self.assertTrue(agent._validate_args({}))

    def test_list_customer_orders_unauthorized_without_context(self):
        """Tool safely fails if called outside an authenticated RequestContext."""
        from agents.unified_agent import list_customer_orders
        
        # Without setting contextvar
        result = list_customer_orders()
        self.assertIn("Lỗi: Không tìm thấy ngữ cảnh yêu cầu", result)

    def test_list_customer_orders_success(self):
        """Tool calls backend /api/orders with Bearer token and formats results cleanly."""
        from agents.unified_agent import list_customer_orders
        import agents.unified_agent as ua
        
        mock_req = MagicMock()
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = [
            {
                "orderNumber": "ORD-001",
                "status": "SHIPPING",
                "finalAmountMinor": 250000,
                "currency": "VND",
                "orderDate": "2026-08-01T10:00:00Z",
                "items": [{"id": "item-1"}, {"id": "item-2"}],
                "shippingAddress": "123 Secret St"  # Private field that MUST NOT be exposed
            },
            {
                "orderNumber": "ORD-002",
                "status": "DELIVERED",
                "finalAmountMinor": 500000,
                "currency": "VND",
                "orderDate": "2026-07-25T14:30:00Z",
                "items": [{"id": "item-3"}]
            }
        ]
        mock_req.get.return_value = mock_response

        with patch.object(ua, "requests", mock_req):
            token_reset = current_request_context.set(self.sample_ctx)
            try:
                result = list_customer_orders(status_filter="SHIPPING")
                self.assertIn("Tìm thấy 2 đơn hàng của bạn", result)
                self.assertIn("ORD-001", result)
                self.assertIn("SHIPPING", result)
                self.assertIn("250000 VND", result)
                # Ensure PII is not leaked by tool formatter
                self.assertNotIn("123 Secret St", result)
                
                # Verify request params & auth header
                mock_req.get.assert_called_once_with(
                    "http://localhost:8081/api/orders",
                    headers={"Authorization": "Bearer mock_bearer_token"},
                    params={"status": "SHIPPING"},
                    timeout=5
                )
            finally:
                current_request_context.reset(token_reset)

    def test_list_customer_orders_empty(self):
        """Tool returns friendly message when no orders found."""
        from agents.unified_agent import list_customer_orders
        import agents.unified_agent as ua
        
        mock_req = MagicMock()
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = []
        mock_req.get.return_value = mock_response

        with patch.object(ua, "requests", mock_req):
            token_reset = current_request_context.set(self.sample_ctx)
            try:
                result = list_customer_orders(status_filter="CANCELLED")
                self.assertIn("Bạn hiện không có đơn hàng nào", result)
                self.assertIn("CANCELLED", result)
            finally:
                current_request_context.reset(token_reset)

    def test_telemetry_records_metrics_without_leaking_content(self):
        """Telemetry logs operational counts and timing without storing prompts or PII."""
        telemetry = TurnTelemetry(
            session_id="sess-abc-123",
            tenant="v-erp-app",
            user_id="usr-12345"
        )
        telemetry.record_tool_call("list_customer_orders")
        telemetry.record_tool_call("list_customer_orders")
        telemetry.record_tool_call("get_order_status")
        telemetry.retry_count = 1
        telemetry.finish(status="success")

        self.assertEqual(telemetry.status, "success")
        self.assertEqual(telemetry.tool_calls["list_customer_orders"], 2)
        self.assertEqual(telemetry.tool_calls["get_order_status"], 1)
        self.assertEqual(telemetry.retry_count, 1)
        self.assertGreaterEqual(telemetry.duration_ms, 0)
        
        # Verify no message content fields exist in TurnTelemetry
        self.assertFalse(hasattr(telemetry, "prompt"))
        self.assertFalse(hasattr(telemetry, "user_message"))
        self.assertFalse(hasattr(telemetry, "tool_results"))


if __name__ == "__main__":
    unittest.main()
