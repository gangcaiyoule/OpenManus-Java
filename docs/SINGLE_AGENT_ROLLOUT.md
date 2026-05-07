# Single-Agent Rollout Playbook

This playbook describes how to release the single-agent flattened architecture safely.

## 1) Release order

1. Deploy backend with unified streaming endpoint only:
- `/api/agent/workflow-stream`

2. Verify no client still depends on removed legacy stream endpoint.

3. Observe error-rate and latency during canary.

## 2) Pre-release gate

Run:

```bash
./scripts/validate-single-agent.sh
```

For ad-hoc Maven commands, prefer:

```bash
./scripts/mvnw-local.sh <mvn-args>
```

Script permissions must be executable in repository/CI:
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

Expected:
- compile succeeds
- all tests pass
- pre-release gate retries tests once only for transient surefire bootstrap error (`Unable to access jarfile .../surefirebooter-*.jar`), clears `target/surefire` before retry, and preserves `target/surefire-reports`.
- if retry still fails, use preserved `target/surefire-reports` from the first attempt for diagnostics.
- retry logs include stable CI tags for signature match, cleanup, retry start, and completion: `VALIDATE_RETRY_SIGNATURE_MATCHED`, `VALIDATE_RETRY_CLEANUP`, `VALIDATE_RETRY_STARTED`, `VALIDATE_RETRY_COMPLETED`.

If it stops on `JAVA_HOME` check, set:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

On non-macOS, set `JAVA_HOME` to your JDK root path directly.
If canonical-path resolution for `javac` is unavailable, the helper script will require manual `JAVA_HOME`.

## 3) Gray-release metrics

Track these indicators during canary and first 24h:

- HTTP error rate for:
  - `/api/agent/chat`
  - `/api/agent/workflow-stream`
- HTTP status mix:
  - `400` ratio (client input quality)
  - `503` ratio (service capacity/executor pressure)
  - `500` ratio (unexpected internal failures / fallback mapping)
  - conflicting `errorCode` vs `error` message responses (should always follow `errorCode`)
  - retry success rate after `503`
- error code mix on streaming failures:
  - `INPUT_INVALID`
  - `ASYNC_SUBMIT_REJECTED`
  - `ASYNC_SUBMIT_EXCEPTION`
  - `INTERNAL_ERROR`
  - `UNKNOWN_ERROR`
- P95/P99 latency:
  - `/api/agent/chat?sync=true`
  - `/api/agent/chat?sync=false`
- tool error rate:
  - file path escape blocked count
  - python file execution failures
- memory continuity smoke signal:
  - same `conversationId` multi-turn success ratio

## 4) Operational checks

- Verify session isolation:
  - data written in session A is not readable in session B.
- Verify legacy session-id mapping behavior:
  - non-conforming `conversationId/sessionId` is mapped to file sandbox directory `legacy-<sha256>`.
  - blank/null session id is rejected for file sandbox operations.

## 5) Legacy Mapping Logging

- Feature flags:
  - `openmanus.legacy.mapping.warn.enabled` (default: `false`)
  - `openmanus.legacy.mapping.warn.sample-rate` (default: `200`, sampled only when warn is enabled)
- Invalid `openmanus.legacy.mapping.warn.sample-rate` values (non-numeric or `<=0`) fallback to `200`.
- Default behavior keeps legacy mapping logs at `debug`; sampled `warn` is opt-in.
- `AgentExecutionTracker` 的全局监听接口仅保留兼容；新增代码必须使用按会话分桶的 `addListener(sessionId, listener)`。

## 6) Rollback plan

If any key indicator regresses:

1. Route traffic back to previous stable backend version.
2. Keep client endpoint at `/api/agent/workflow-stream`.
3. Capture failed request samples and session IDs.
4. Re-run regression suite locally and patch.
5. Re-enter gray-release with 5% traffic, then 25%, then 100%.
