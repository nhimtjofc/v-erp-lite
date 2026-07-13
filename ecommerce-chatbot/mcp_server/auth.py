def verify_signature(payload, signature):
    """
    Verifies that the incoming request is cryptographically signed
    by an authorized AI Agent, ensuring LLM cannot execute raw APIs.
    """
    # Mock implementation
    expected_signature = "valid_mcp_sig"
    return signature == expected_signature
