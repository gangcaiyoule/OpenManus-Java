# Single-Agent Flattening Validation

This document validates the migration from multi-agent handoff architecture to a single-agent unified workflow.

## Scope

- One executor loop: `UnifiedAgent extends AbstractAgentExecutor`.
- One workflow core: `AgentExecutionService` plus `ExecutionStreamingApplicationService`.
- Unified tool mounting: browser/file/python/reflection tools are directly mounted on `UnifiedAgent`.
- Session memory continuity: runtime-first `AiMemory` backed by `FileChatMemoryStore` or `InMemoryAiMemoryStore`.
- Sandbox enforcement: file, shell, and Python execution are constrained by `AiSessionSandboxGateway` and sandbox adapters.

## Prerequisites

Recommended entrypoint for all local checks: `./scripts/validate-single-agent.sh`.
If you need to run Maven directly, use `./scripts/mvnw-local.sh ...` to avoid shell-specific `JAVA_HOME` drift.

`validate-single-agent.sh` will auto-detect `JAVA_HOME` on macOS (`/usr/libexec/java_home -v 21`) and also tries `javac`-based detection on non-macOS.
The `javac` fallback uses resolved real paths (symlink-safe) to avoid mis-detecting `/usr/bin/javac` as JDK root; if canonical-path resolution is unavailable, it will ask for manual `JAVA_HOME`.
If auto-detection fails, set it manually:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## 1) Static architecture checks

```bash
rg -n \
  "AgentHandoff|ThinkingAgent|SearchAgent|CodeAgent|FileAgent|ReflectionAgent|FastThinkWorkflow|ThinkDoReflectWorkflow|ThinkDoReflectService|SubAgentConfig" \
  src/main/java src/test/java
```

Expected:
- no matches in source or test code.
- this check is intentionally aligned with `scripts/validate-single-agent.sh` allowlist behavior.

## 2) Compile main code

```bash
./scripts/mvnw-local.sh -q -DskipTests compile
```

Expected:
- build succeeds.

## 3) Run migration regression tests

```bash
./scripts/mvnw-local.sh -q test
```

Expected:
- `AgentCoordinatorSmokeTest`: runtime-first single-agent loop handles tool planning and execution.
- `AgentToolResultBudgetE2ETest`: large tool outputs are written to sandbox files and replaced with explicit stubs.
- `PythonExecutionToolSmokeTest`: Python execution remains sandboxed and failure paths are handled.
- `SearchToolSmokeTest`, `ShellToolSmokeTest`, `WebFetchToolSmokeTest`, `BrowserToolSmokeTest`, `TaskReflectionToolSmokeTest`: core toolchain remains available after the migration.
- `AgentControllerSessionSandboxStartTest`: web entrypoints stay consistent with the new infra/web architecture.
- `FrontendProxyControllerTest`, `WebProxyControllerTest`: proxy controllers remain functional.
- `validate-single-agent.sh` retries regression tests once only when Maven reports transient surefire bootstrap error (`Unable to access jarfile .../surefirebooter-*.jar`), clears `target/surefire` before retry, and preserves `target/surefire-reports` for diagnostics.
- if the retry still fails, inspect `target/surefire-reports` from the first attempt for root-cause details.
- retry flow logs stable CI tags for signature match, cleanup, retry start, and retry completion: `VALIDATE_RETRY_SIGNATURE_MATCHED`, `VALIDATE_RETRY_CLEANUP`, `VALIDATE_RETRY_STARTED`, `VALIDATE_RETRY_COMPLETED`.

## Quick command

You can run the full gate with one command:

```bash
./scripts/validate-single-agent.sh
```

Repository scripts should keep executable bits:
- `scripts/validate-single-agent.sh`
- `scripts/mvnw-local.sh`

If executable bits are lost in git metadata, fix with:

```bash
git update-index --chmod=+x scripts/validate-single-agent.sh scripts/mvnw-local.sh
```

Optional CI pre-check:

```bash
test -x scripts/validate-single-agent.sh -a -x scripts/mvnw-local.sh
```

## 4) API checks

Endpoints:
- `POST /api/agent/chat` (unified HTTP chat)
- `POST /api/agent/workflow-stream` (unified streaming endpoint)

Expected:
- unified streaming path executes the single-agent workflow service.
- removed legacy path `/api/agent/think-do-reflect-stream` returns `404 Not Found`.
- status code contract for streaming endpoint errors:
  - `400` for `errorCode=INPUT_INVALID`
  - `503` for `errorCode=ASYNC_SUBMIT_REJECTED|ASYNC_SUBMIT_EXCEPTION`
  - `500` for unexpected server-side errors (including unknown/absent `errorCode`)

## 5) Manual conversation/memory verification

1. Send request A to `/api/agent/chat` with `stateful=true`, `conversationId=conv-1`.
2. Send request B to `/api/agent/chat` with the same `conversationId=conv-1`.
3. Ask B to reference facts from A.

Expected:
- B can leverage message history without string-summary handoff.
- By default, full tool-result messages are preserved in chat memory.
- Oversized tool results are optionally offloaded only when `openmanus.chat-memory.tool-result-budget-enabled=true`.
- Chat-memory config precedence is `explicit config > env/system fallback > defaults`.
- Current model requests no longer apply local message-window trimming, historical summarization, or token-budget clipping.

Recommended Manus-style tuning:
- Keep `tool-result-budget-enabled=false` when you want full tool outputs inline.
- Enable `tool-result-budget-enabled=true` when one `execute` may produce very large tool outputs and you prefer explicit shell reads over larger provider payloads.

## 6) Sandbox isolation verification

1. Use session `s1` to write `notes/a.txt`.
2. Use session `s2` to read `notes/a.txt`.

Expected:
- `s2` cannot read `s1` data.
- traversal like `../outside.txt` is rejected in both file and python-file tools.

## 7) Legacy session-id mapping and logging

- File sandbox id rules:
  - valid id pattern: `^[a-zA-Z0-9_-]{1,64}$`
  - invalid nonblank id: mapped to `legacy-<sha256>`
  - blank/null id: rejected
- Logging controls:
  - `openmanus.legacy.mapping.warn.enabled` default `false`
  - `openmanus.legacy.mapping.warn.sample-rate` default `200`
  - invalid sample-rate values (non-numeric / `<=0`) fallback to `200`
- Listener API policy:
  - deprecated global listener APIs are compatibility-only; new code must use `addListener(sessionId, listener)`.

## Rollout note

- Streaming API is unified at `/api/agent/workflow-stream`.
- Legacy stream endpoint `/api/agent/think-do-reflect-stream` has been removed.

## Notes

- `pom.xml` excludes `src/test/java/com/openmanus/java/adaptiverag/**` from test compilation because those are optional experimental tests with undeclared dependencies and unrelated to this migration.
- rollout and rollback execution details are documented in `docs/SINGLE_AGENT_ROLLOUT.md`.
