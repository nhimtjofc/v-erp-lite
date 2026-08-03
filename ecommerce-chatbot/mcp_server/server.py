from fastapi import FastAPI, Header, HTTPException, Depends
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
import sys
import os
from dotenv import load_dotenv

load_dotenv()

orig_stdout_write = sys.stdout.write
orig_stderr_write = sys.stderr.write

def safe_stdout_write(s):
    try:
        return orig_stdout_write(s)
    except Exception:
        return orig_stdout_write(s.encode('ascii', 'replace').decode('ascii'))

def safe_stderr_write(s):
    try:
        return orig_stderr_write(s)
    except Exception:
        return orig_stderr_write(s.encode('ascii', 'replace').decode('ascii'))

sys.stdout.write = safe_stdout_write
sys.stderr.write = safe_stderr_write

# Fix path so we can import agents from parent dir
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from agents.unified_agent import UnifiedAgent
from mcp_server.session_store import (
    default_store,
    SessionBusy,
    SessionNotFound,
    SessionAccessDenied,
)
from mcp_server.request_context import (
    verify_bearer,
    AuthError,
    RequestContext,
    current_request_context,
)
from mcp_server.telemetry import TurnTelemetry

app = FastAPI(title="Ecommerce AI Chatbot")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

print("SERVER IS STARTING. API KEY IS:", os.environ.get("GEMINI_API_KEY"))
unified_agent = UnifiedAgent()

class ChatRequest(BaseModel):
    # Only a new message and a server-issued session id — never client history.
    # See intern-ai-agent-design.md: accepting history enables forged system/assistant/tool messages.
    message: str
    session_id: Optional[str] = None


def require_caller(authorization: Optional[str] = Header(default=None)) -> RequestContext:
    """FastAPI dependency: authenticate the caller from the Bearer token.

    Identity is derived here and passed to the agent — the model never sees it.
    """
    try:
        return verify_bearer(authorization)
    except AuthError as e:
        # 401 with a generic message; no crypto/internal detail leaks out.
        raise HTTPException(status_code=401, detail=str(e))


@app.post("/chat")
async def chat(req: ChatRequest, caller: RequestContext = Depends(require_caller)):
    # Propagate trusted RequestContext via contextvar
    token_reset = current_request_context.set(caller)
    session_id = req.session_id
    if not session_id:
        session_id = default_store.create(caller)

    telemetry = TurnTelemetry(
        session_id=session_id,
        tenant=caller.tenant,
        user_id=caller.user_id
    )

    try:
        user_msg = req.message
        print(f"[chat] session={session_id} tenant={caller.tenant} user={caller.user_id}")

        with default_store.turn(session_id, caller):
            # Load server-owned history
            history = default_store.load(session_id, caller)

            # Process query via UnifiedAgent with Bounded Tool Loop & Output Validation
            answer, updated_history, is_valid = unified_agent.handle_query_with_history(
                user_msg,
                history,
                telemetry=telemetry
            )

            # Atomicity: Append only on valid completion (Rule #4 & #5)
            if is_valid:
                new_messages = updated_history[len(history):]
                new_messages_dicts = [type(msg).to_dict(msg) for msg in new_messages]
                default_store.append_turn(session_id, caller, new_messages_dicts)
                telemetry.finish(status="success")
            else:
                telemetry.finish(status="validation_fallback")

        html_answer = answer.replace('\n', '<br>')
        return {
            "response": html_answer,
            "session_id": session_id
        }

    except SessionBusy:
        telemetry.finish(status="busy", error_type="SessionBusy")
        raise HTTPException(
            status_code=409,
            detail="A turn is already in progress for this session."
        )
    except (SessionNotFound, SessionAccessDenied):
        telemetry.finish(status="access_denied", error_type="SessionAccessDenied")
        raise HTTPException(
            status_code=404,
            detail="Session not found or access denied."
        )
    except Exception as e:
        # Hide traceback in production/UI to prevent stack trace leaks
        import sys
        print(f"[ERROR] Chat route failed: {e}", file=sys.stderr)
        telemetry.finish(status="error", error_type=type(e).__name__)
        return {
            "response": "Dạ hệ thống hỗ trợ đang bận hoặc gặp sự cố nhỏ, bạn vui lòng thử lại sau nhé.",
            "session_id": req.session_id
        }
    finally:
        current_request_context.reset(token_reset)


@app.get("/")
async def get_ui():
    html_content = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Ecommerce AI Demo</title>
        <meta charset="utf-8">
        <style>
            body { font-family: 'Inter', sans-serif; background: #1e1e2e; color: #cdd6f4; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
            #chat-container { width: 450px; height: 600px; background: rgba(30, 30, 46, 0.8); backdrop-filter: blur(20px); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 20px; padding: 20px; display: flex; flex-direction: column; box-shadow: 0 20px 50px rgba(0,0,0,0.5); }
            #messages { flex: 1; overflow-y: auto; margin-bottom: 20px; display: flex; flex-direction: column; gap: 10px; padding-right: 5px; }
            .msg { max-width: 85%; width: fit-content; box-sizing: border-box; word-break: break-word; text-wrap: pretty; padding: 12px 16px; border-radius: 15px; line-height: 1.5; font-size: 15px; }
            .user-msg { background: #89b4fa; color: #11111b; align-self: flex-end; border-bottom-right-radius: 5px; }
            .ai-msg { background: #313244; align-self: flex-start; border-bottom-left-radius: 5px; }
            .input-area { display: flex; gap: 10px; }
            input { flex: 1; padding: 15px; border: none; background: #181825; color: #cdd6f4; border-radius: 12px; outline: none; font-size: 15px; border: 1px solid rgba(255, 255, 255, 0.05); }
            button { padding: 0 20px; border: none; background: #a6e3a1; color: #11111b; font-weight: bold; cursor: pointer; border-radius: 12px; font-size: 15px; transition: 0.2s; }
            button:hover { background: #94e28f; transform: scale(1.05); }
        </style>
    </head>
    <body>
        <div id="chat-container">
            <h2 style="text-align: center; margin-top: 0; margin-bottom: 20px; color: #f5c2e7;">✨ AI Shopping Assistant</h2>
            <div id="messages">
                <div class="msg ai-msg">Xin chào! Tôi là trợ lý AI. Bạn muốn tìm sản phẩm nào hay tra cứu tình trạng đơn hàng?</div>
            </div>
            <div class="input-area">
                <input type="text" id="userInput" placeholder="Nhập câu hỏi... (VD: Đơn hàng của tôi...)" onkeypress="if(event.key === 'Enter') sendMessage()">
                <button onclick="sendMessage()">Gửi</button>
            </div>
        </div>
        <script>
            async function sendMessage() {
                const input = document.getElementById("userInput");
                const messages = document.getElementById("messages");
                if (!input.value.trim()) return;
                
                messages.innerHTML += `<div class="msg user-msg">${input.value}</div>`;
                const text = input.value;
                input.value = "";
                
                const loadingId = "loading-" + Date.now();
                messages.innerHTML += `<div id="${loadingId}" class="msg ai-msg">⏳ Đang xử lý...</div>`;
                messages.scrollTop = messages.scrollHeight;
                
                const response = await fetch("/chat", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({message: text})
                });
                const data = await response.json();
                
                document.getElementById(loadingId).remove();
                messages.innerHTML += `<div class="msg ai-msg">${data.response}</div>`;
                messages.scrollTop = messages.scrollHeight;
            }
        </script>
    </body>
    </html>
    """
    return HTMLResponse(content=html_content)
