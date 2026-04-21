#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

readonly RETRY_SIGNATURE_MATCHED_TAG="VALIDATE_RETRY_SIGNATURE_MATCHED"
readonly RETRY_CLEANUP_TAG="VALIDATE_RETRY_CLEANUP"
readonly RETRY_STARTED_TAG="VALIDATE_RETRY_STARTED"
readonly RETRY_COMPLETED_TAG="VALIDATE_RETRY_COMPLETED"

if [[ ! -x "./scripts/mvnw-local.sh" ]]; then
  echo "ERROR: ./scripts/mvnw-local.sh is not executable."
  echo "Please run: chmod +x scripts/mvnw-local.sh scripts/validate-single-agent.sh"
  exit 1
fi

echo "[1/3] Static scan for forbidden legacy classes..."
FORBIDDEN_PATTERN="AgentHandoff|ThinkingAgent|SearchAgent|CodeAgent|FileAgent|ReflectionAgent|FastThinkWorkflow|ThinkDoReflectWorkflow|ThinkDoReflectService|SubAgentConfig"

# 1) Token-level scan (do not depend on 'class' declarations)
# Keep a centralized allowlist for known intentional references (e.g. architecture guard tests).
WHITELIST_GLOBS=(
  "!src/test/java/com/openmanus/infra/architecture/SingleAgentArchitectureGuardTest.java"
)
RG_ARGS=(-n)
for glob in "${WHITELIST_GLOBS[@]}"; do
  RG_ARGS+=(--glob "$glob")
done

if rg "${RG_ARGS[@]}" "${FORBIDDEN_PATTERN}" src/main/java src/test/java; then
  echo "ERROR: legacy multi-agent identifiers detected."
  exit 1
fi

# 2) Path-level existence guard
LEGACY_PATHS=(
  "src/main/java/com/openmanus/agent/base/AgentHandoff.java"
  "src/main/java/com/openmanus/agent/impl/thinker/ThinkingAgent.java"
  "src/main/java/com/openmanus/agent/impl/executor/SearchAgent.java"
  "src/main/java/com/openmanus/agent/impl/executor/CodeAgent.java"
  "src/main/java/com/openmanus/agent/impl/executor/FileAgent.java"
  "src/main/java/com/openmanus/agent/impl/reflection/ReflectionAgent.java"
  "src/main/java/com/openmanus/agent/workflow/FastThinkWorkflow.java"
  "src/main/java/com/openmanus/agent/workflow/ThinkDoReflectWorkflow.java"
  "src/main/java/com/openmanus/domain/service/ThinkDoReflectService.java"
  "src/main/java/com/openmanus/infra/config/SubAgentConfig.java"
)

for legacy_path in "${LEGACY_PATHS[@]}"; do
  if [[ -e "$legacy_path" ]]; then
    echo "ERROR: legacy file still exists: $legacy_path"
    exit 1
  fi
done

echo "[2/3] Compile..."
./scripts/mvnw-local.sh -q -DskipTests compile

echo "[3/3] Regression tests..."
TEST_ARGS=(
  -q
  "-Dtest=SingleAgentArchitectureGuardTest,ValidationScriptsConsistencyTest,MvnwLocalScriptIntegrationTest,UnifiedWorkflowTest,AgentControllerStreamEndpointTest,AgentControllerServiceContractTest,AgentServiceConversationMemoryTest,WorkflowStreamServiceSessionMemoryTest,FileToolSandboxTest,PythonToolSandboxPathTest,LangChain4jConfigChatMemoryTest"
  test
)
LOG_FILE="$(mktemp)"
trap 'rm -f "$LOG_FILE"' EXIT

if ! ./scripts/mvnw-local.sh "${TEST_ARGS[@]}" 2>&1 | tee "$LOG_FILE"; then
  if grep -Eq 'Unable to access jarfile .*/surefirebooter-.*\.jar' "$LOG_FILE"; then
    echo "WARN: [${RETRY_SIGNATURE_MATCHED_TAG}] matched transient surefire bootstrap signature: Unable to access jarfile .../surefirebooter-*.jar"
    echo "WARN: [${RETRY_CLEANUP_TAG}] cleaning transient surefire temp directory before one-time retry: target/surefire"
    rm -rf target/surefire
    echo "WARN: [${RETRY_STARTED_TAG}] retrying regression tests once..."
    ./scripts/mvnw-local.sh "${TEST_ARGS[@]}"
    echo "INFO: [${RETRY_COMPLETED_TAG}] regression retry completed."
  else
    exit 1
  fi
fi

echo "Single-agent validation passed."
