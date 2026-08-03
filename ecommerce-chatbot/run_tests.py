"""
Offline Test Runner for AI Agent Hardening and Security Invariants.
Runs all test modules using pure Python standard library without requiring pytest.
"""
import sys
import os
import unittest
import inspect
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def run_all_tests():
    total = 0
    passed = 0
    failed = 0
    errors = []

    print("=" * 70)
    print("  RUNNING AI AGENT HARDENING & SECURITY INVARIANT TESTS")
    print("=" * 70)

    # 1. Run unittest TestCase based tests (test_agent_loop.py)
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()

    try:
        from tests.test_agent_loop import TestAgentLoopAndTools
        suite.addTests(loader.loadTestsFromTestCase(TestAgentLoopAndTools))
    except Exception as e:
        print(f"Error loading test_agent_loop: {e}")

    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    passed += result.testsRun - len(result.failures) - len(result.errors)
    failed += len(result.failures) + len(result.errors)
    total += result.testsRun

    # 2. Run function-based tests in test_session_store.py and test_output_validator.py
    import tests.test_session_store as t_store
    import tests.test_output_validator as t_val

    def run_module_functions(mod, name):
        nonlocal total, passed, failed, errors
        print(f"\nRunning {name}...")
        for attr in dir(mod):
            if attr.startswith("test_"):
                fn = getattr(mod, attr)
                if callable(fn):
                    total += 1
                    try:
                        sig = inspect.signature(fn)
                        if "store" in sig.parameters:
                            from mcp_server.session_store import InMemorySessionStore
                            fn(InMemorySessionStore(max_messages=5))
                        else:
                            fn()
                        passed += 1
                        print(f"  [PASS] {attr}")
                    except Exception as e:
                        failed += 1
                        print(f"  [FAIL] {attr}: {e}")
                        errors.append((f"{name}.{attr}", traceback.format_exc()))

    run_module_functions(t_store, "test_session_store.py")
    run_module_functions(t_val, "test_output_validator.py")

    # 3. Run test_request_context.py (claim-resolution and denial tests)
    import tests.test_request_context as t_rc
    print("\nRunning test_request_context.py (unit checks)...")
    for attr in ["test_tenant_prefers_organization_array", "test_tenant_falls_back_to_tenant_code_then_azp_then_default",
                 "test_roles_extracted_from_realm_access", "test_missing_header_denied", "test_malformed_header_denied"]:
        if hasattr(t_rc, attr):
            total += 1
            fn = getattr(t_rc, attr)
            try:
                fn()
                passed += 1
                print(f"  [PASS] {attr}")
            except Exception as e:
                failed += 1
                print(f"  [FAIL] {attr}: {e}")
                errors.append((f"test_request_context.{attr}", traceback.format_exc()))

    print("\n" + "=" * 70)
    print(f"TEST SUMMARY: Total={total} | Passed={passed} | Failed={failed}")
    print("=" * 70)

    if errors:
        print("\nFAILURES / ERRORS:")
        for test_name, tb in errors:
            print(f"\n--- {test_name} ---")
            print(tb)
        return 1
    return 0


if __name__ == "__main__":
    exit_code = run_all_tests()
    sys.exit(exit_code)
