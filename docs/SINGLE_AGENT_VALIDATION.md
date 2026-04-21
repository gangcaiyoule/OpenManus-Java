# Single-Agent Flattening Validation

This document validates the migration from multi-agent handoff architecture to a single-agent unified workflow.

## Scope

- One executor loop: `UnifiedAgent extends AbstractAgentExecutor`.
- One workflow core: `UnifiedWorkflow`.
- Unified tool mounting: browser/file/python/reflection tools are directly mounted on `UnifiedAgent`.
- Session memory continuity: `PersistentChatMemory` backed by `FileChatMemoryStore`.
- Sandbox enforcement: file and python-file operations are constrained by `SessionSandboxManager`.

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
  --glob '!src/test/java/com/openmanus/infra/architecture/SingleAgentArchitectureGuardTest.java' \
  "AgentHandoff|ThinkingAgent|SearchAgent|CodeAgent|FileAgent|ReflectionAgent|FastThinkWorkflow|ThinkDoReflectWorkflow|ThinkDoReflectService|SubAgentConfig" \
  src/main/java src/test/java
```

Expected:
- no matches after excluding the architecture guard test file.
- this check is intentionally aligned with `scripts/validate-single-agent.sh` allowlist behavior.

## 2) Compile main code

```bash
./scripts/mvnw-local.sh -q -DskipTests compile
```

Expected:
- build succeeds.

## 3) Run migration regression tests

```bash
./scripts/mvnw-local.sh -q -Dtest=SingleAgentArchitectureGuardTest,ValidationScriptsConsistencyTest,MvnwLocalScriptIntegrationTest,UnifiedWorkflowTest,AgentControllerStreamEndpointTest,AgentControllerServiceContractTest,AgentServiceConversationMemoryTest,WorkflowStreamServiceSessionMemoryTest,FileToolSandboxTest,PythonToolSandboxPathTest,RuntimeChatMemoryProviderTest test
```

Expected:
- `AgentServiceConversationMemoryTest`: conversation id is forwarded; mode is `unified`; MDC session id is bound.
- `UnifiedWorkflowTest`: null/blank input is rejected before agent execution; conversation id is forwarded as memory id.
- `WorkflowStreamServiceSessionMemoryTest`: session id is forwarded to unified workflow memory.
- `FileToolSandboxTest`: read/write works in session sandbox; `../` traversal is blocked; files are isolated across sessions.
- `PythonToolSandboxPathTest`: python file is read from session sandbox; traversal is blocked.
- `RuntimeChatMemoryProviderTest`: same conversation id keeps message history across provider fetches.
- `AgentControllerStreamEndpointTest`: unified streaming endpoint is available; removed legacy path returns `404`; unified endpoint keeps stable error mapping behavior.
- `AgentControllerServiceContractTest`: verifies real service->controller contract for `errorCode -> HTTP status` (`400/503/500`) on unified path, including `INTERNAL_ERROR -> 500` (listener-registration failure), verifies that `errorCode` takes precedence over legacy `error` message when both exist but conflict, and additionally verifies controller fallback mapping for `UNKNOWN_ERROR -> 500`.
- `SingleAgentArchitectureGuardTest`: legacy multi-agent classes/config/workflow remain absent.
- `ValidationScriptsConsistencyTest`: validates `validate-single-agent.sh` and `mvnw-local.sh` stay consistent with script entrypoint and safe `JAVA_HOME` inference rules.
- `MvnwLocalScriptIntegrationTest`: executes `mvnw-local.sh` in isolated temp directories to verify argument pass-through, invalid `JAVA_HOME` failure behavior, and no `/usr` fallback when canonical-path resolution is unavailable.
- `validate-single-agent.sh` removes `target/test-classes` before regression tests to avoid stale deleted test classes being executed as false positives.
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
- Tool-result compaction is optional and only active when `openmanus.chat-memory.compact-tool-results-enabled=true`.
- Chat-memory config precedence is `explicit config > env/system fallback > defaults`.
- Context-window governance uses two independent limits:
  - `openmanus.chat-memory.model-context-max-messages`: caps historical messages sent to model per round.
  - `openmanus.chat-memory.model-context-max-total-messages`: caps total model-input messages per round (history + current turn).
- Invalid negative values for the two model-context limits are sanitized to `0` (unlimited).
- Both limits only affect model input payload, not persisted `ChatMemory` full history.
- `model-context-max-total-messages=1` is an extreme mode: prioritize current user message continuity; do not expect system/history to be preserved in that round.
- In tool-loop scenarios, avoid setting `model-context-max-total-messages` too small.
  - `=2` keeps continuity by prioritizing current user + latest tool result.
  - for production tool-heavy sessions, prefer `>=3` (or higher, such as 20/40 depending on model window).
- If current-user identity matching fails in a defensive edge branch, total-limit trimming is still enforced (no bypass of `model-context-max-total-messages`).

Recommended Manus-style tuning:
- Start with `model-context-max-messages=20` and keep `model-context-max-total-messages=0` for continuity-first scenarios.
- If one `execute` may trigger many tool rounds with large outputs, set `model-context-max-total-messages` (for example `40`) as a hard protection cap.
- Keep `compact-tool-results-enabled=false` unless storage/network pressure requires compaction.

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

- rollout and rollback execution details are documented in `docs/SINGLE_AGENT_ROLLOUT.md`.
