from fastapi import FastAPI, Header, HTTPException, Depends
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
import sys
import os

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
from agents.router_agent import RouterAgent
from agents.retrieval_agent import RetrievalAgent
from agents.order_ops_agent import OrderOpsAgent
from agents.stats_agent import StatsAgent
from mcp_server.request_context import verify_bearer, AuthError, RequestContext

app = FastAPI(title="Ecommerce AI Chatbot")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

print("SERVER IS STARTING. API KEY IS:", os.environ.get("GEMINI_API_KEY"))
router = RouterAgent()
retrieval = RetrievalAgent()
order_ops = OrderOpsAgent()
stats = StatsAgent()

class ChatRequest(BaseModel):
    # Only a new message (and, later, a server-issued session id) — never client
    # history. See intern-ai-agent-design.md: accepting history enables forged
    # system/assistant/tool messages.
    message: str


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
    try:
        # caller.tenant / caller.user_id / caller.access_token are now available
        # to scope backend calls per-user (wired into the tools in Step 2).
        print(f"[chat] authenticated caller tenant={caller.tenant} roles={list(caller.roles)}")
        user_msg = req.message
        intent = router.route_query(user_msg)
        
        if intent == "STORE_STATS":
            answer = stats.handle_query(user_msg)
        elif intent == "ORDER_OPS":
            answer = order_ops.handle_query(user_msg)
        else:
            answer = retrieval.handle_query(user_msg)
            
        html_answer = answer.replace('\n', '<br>')
        return {"response": f"🤖 Ý định: <b>{intent}</b><br><hr style='border:0; border-top:1px solid rgba(255,255,255,0.1); margin:10px 0;'>{html_answer}"}
    except Exception as e:
        import traceback
        tb = traceback.format_exc()
        return {"response": f"❌ Lỗi Server: <pre>{tb}</pre>"}

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
                messages.innerHTML += `<div id="${loadingId}" class="msg ai-msg">⏳ Đang vắt óc suy nghĩ... (Có thể mất đến 1 phút nếu bị kẹt mạng)</div>`;
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
