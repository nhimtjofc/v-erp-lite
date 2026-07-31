# Intern project: build a safe ERP / e-commerce AI agent

## Purpose

Build a customer-facing AI agent for an ERP or e-commerce application. It helps
users find information and initiate approved workflows through the application's
existing APIs. The application remains the source of truth: the model decides
*what* it wants to look up or request, while application code decides *who* may
access it and *whether* an operation is allowed.

This is an implementation brief, not a description of any existing product. Keep
the first version small: one chat endpoint, a bounded tool-calling loop, a small
read-only tool set, and tests for the invariants below.

## Mental model

```
authenticated request
  -> trusted request context (organization + user)
  -> load server-owned conversation
  -> model selects a tool and semantic arguments
  -> tool calls application API with trusted context
  -> model turns returned data into a helpful reply
  -> validate reply, then append the completed turn to the session
```

The agent should understand requests, retrieve authorized information, and
summarize results. It must not become an authorization system, a database owner,
or an executor of irreversible business actions.

## MVP scope

Start with a read-only assistant. Choose 3–4 generic, bounded capabilities that
match the application, such as:

- search products, catalog items, or inventory;
- retrieve the caller's orders, invoices, returns, or shipments;
- retrieve account, policy, or help-center information; and
- retrieve a status or exception for a caller-visible business object.

Use semantic arguments only. For example, a tool may accept `query`, `status`,
`date_range`, or a record ID that the caller already has permission to see. It
must not accept an organization ID, user ID, customer ID, account ID, or similar
identity selector.

Defer writes. If a later iteration needs an action (for example, submitting a
change request), it must create a **pending request** for an existing approval
workflow. It must not directly modify orders, inventory, payments, fulfillment,
accounting records, or customer data.

The API receives only a new message and an optional server-issued session ID:

```text
POST /chat  { "session_id": "optional", "message": "Where is my order?" }
```

Do not accept client-provided history; it enables forged system, assistant, and
tool messages.

## Non-negotiable design rules

### 1. Identity stays outside the model

Authenticate before invoking the agent. Derive the organization/tenant and user
from a signed credential or trusted server session, then place them in a
request-scoped context object.

```python
# Incorrect: the model can be influenced to name another principal.
get_records(customer_id: str, status: str)

# Correct: identity is host-controlled and invisible to the tool schema.
get_records(status: str) -> app_api.get_records(request_context, status=status)
```

Every application API call must be scoped by both organization and user (and
their role/permissions where applicable). Use the normal application service
layer, never a privileged agent credential.

### 2. Tool results are untrusted data

Catalog text, user-generated content, ERP records, knowledge-base articles, and
even operator-authored notes can contain prompt injection. Return them as tool
messages; never splice them into system instructions. A tool result must not be
able to alter permissions, override policy, reveal secrets, or confirm an action.

Keep deterministic business rules in application code. Validation of stock,
prices, eligibility, permissions, accounting rules, and state transitions belongs
in the backend—not in the model's reasoning.

### 3. Keep shared prompt and schemas stable

Prompt-prefix caching makes repeated input cheaper and faster. Construct prompts
in decreasing order of stability:

```text
[platform-wide system rules + fixed tool schemas]
[organization configuration and capability hint]
[append-only session conversation]
```

Never put a user ID, session ID, current time, request ID, or organization-specific
tool list in the platform-wide block. Do not vary tool schemas by organization or
role. Keep schemas fixed and enforce feature availability and authorization inside
the tool/application layer.

### 4. Sessions are server-owned and append-only

Store conversation history on the server. After a successful turn, append the
user message, raw assistant message, and exactly one tool result for each tool
call. Avoid per-turn sliding windows: they rewrite the prefix, reduce cache reuse,
and can corrupt tool-call/tool-result pairing.

Round-trip the provider's raw assistant message instead of rebuilding a smaller
shape. Providers may require extra fields in later requests. Bound session size
for the MVP; if long sessions become necessary, add a deliberate, tested
compaction event rather than trimming on every turn.

### 5. Validate output before delivery

Use deterministic checks for known high-risk failures. At minimum, reject or
retry a response that reveals protected instructions, fabricates a record/status/
price/policy, or claims that an action succeeded without application confirmation.

Make one corrective no-tool retry, then return a safe fallback. Do not persist
rejected output in normal history. Validate before streaming any response.

## Components

| Component | Responsibility | Must not do |
|---|---|---|
| Auth | Verify credentials; bind organization + user | Trust caller-provided identity in production |
| Request context | Carry trusted identity and permissions to tools | Appear in schemas or prompt prefix |
| Provider adapter | Call the model; normalize usage | Implement authorization |
| Agent loop | Call model, validate/dispatch tools, bound steps | Reconstruct provider messages |
| Tool registry | Fixed schemas and typed argument validation | Vary schemas per caller |
| Application port | Typed, permission-scoped API contract | Expose database/admin credentials |
| Session store | Ownership-scoped, append-only messages | Accept client-provided history |
| Output validator | Deterministic final-reply checks | Replace backend validation |
| Telemetry | Counts, latency, token/cache usage, errors | Store prompts, PII, arguments, or results |

## Agent loop pseudocode

```python
messages = stable_system + organization_context + stored_history + [new_user]
appended = []

for step in range(MAX_STEPS):
    completion = provider.complete(messages + appended, fixed_tool_schemas)
    appended.append(completion.raw_assistant_message)  # preserve every field

    if not completion.tool_calls:
        reply = validate_or_retry(completion, messages + appended)
        persist_atomically(new_user, appended)
        return reply

    for call in completion.tool_calls:
        result = dispatch_validated_tool(call, request_context)
        appended.append({"role": "tool", "tool_call_id": call.id,
                         "content": result})

raise SafeFailure("I couldn't complete that request. Please try again.")
```

Tool dispatch must safely handle unknown tools, malformed arguments, permission
denials, and backend failures. It must not expose stack traces, secrets, or
internal implementation details to the model or user.

## Delivery plan

1. Define a narrow `ApplicationAPI` interface and fake in-memory adapter. Each
   data method accepts `RequestContext`.
2. Add authentication and request-context binding. Test cross-organization and
   cross-user denial before adding an LLM.
3. Add a fixed, typed tool registry. Reject identity-like schema fields such as
   `user`, `tenant`, `organization`, `customer`, `account`, and equivalents.
4. Implement the bounded tool loop using a scripted fake provider. Test pairing
   of each tool call with a result and maximum-step handling.
5. Add server-owned append-only sessions and ownership checks.
6. Integrate a real provider while preserving raw assistant messages. Measure
   cache usage when the provider exposes it.
7. Add output validation, hostile tool-result tests, limits, and no-content
   telemetry.

## Acceptance criteria

- A user retrieves only data allowed by their organization and permissions.
- A malicious tool result cannot cause unauthorized backend access.
- Tool schemas contain no caller identity fields.
- Tool schemas are byte-identical for organizations with different capabilities.
- Organization context is absent from the shared system block and appears only
  in the later context block.
- Every tool call receives exactly one result; invalid arguments never reach the
  application API.
- Unknown assistant-message fields survive session storage and the next provider
  call.
- Concurrent session turns are rejected rather than interleaved.
- Failed turns persist no partial history.
- Telemetry includes only safe operational metadata, never message or tool data.

## Common mistakes

- Letting a tool accept a principal or organization identifier.
- Giving the agent direct database access or a service-account credential.
- Trusting prompts to enforce rules that backend code can enforce.
- Personalizing the shared prompt or varying schemas by caller.
- Directly executing financial, fulfillment, inventory, or account changes.
- Rebuilding provider messages and dropping unknown fields.
- Logging prompts, tool arguments, or tool results.
- Treating prompt edits as copy changes rather than safety/cost deployments.

## Definition of done

Submit a short README covering the chosen capabilities, API contract, tool list,
threat model, and test instructions. Demonstrate a normal authorized lookup, a
denied cross-organization attempt, and a malicious tool-result injection.
Clearly distinguish structural controls (authentication, scoped application APIs,
typed schemas, approval workflows) from behavioral controls (prompt rules and
output validation).

Prefer a small, explicit, auditable loop over a sophisticated autonomous system.
