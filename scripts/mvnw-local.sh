#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

required_java_major=21

read_java_spec_version() {
  local java_cmd="$1"
  [[ -x "$java_cmd" ]] || return 1

  local version_line
  version_line="$("$java_cmd" -XshowSettings:properties -version 2>&1 \
    | awk -F'= ' '/^[[:space:]]*java\.specification\.version = / {print $2; exit}')"
  version_line="${version_line%%[[:space:]]*}"
  [[ -n "$version_line" ]] || return 1
  printf '%s' "$version_line"
}

java_version_is_supported() {
  local java_home="$1"
  local version
  version="$(read_java_spec_version "$java_home/bin/java" || true)"
  [[ -n "$version" && "$version" =~ ^[0-9]+$ && "$version" -ge "$required_java_major" ]]
}

should_reset_surefire_reports=false
for arg in "$@"; do
  if [[ "$arg" == "test" ]]; then
    should_reset_surefire_reports=true
    break
  fi
done

if [[ "$should_reset_surefire_reports" == true ]]; then
  rm -rf target/surefire-reports
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  if [[ ! -x "${JAVA_HOME}/bin/java" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
    unset JAVA_HOME
  elif ! java_version_is_supported "${JAVA_HOME}"; then
    unset JAVA_HOME
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [[ -n "$JAVA_HOME" ]]; then
      export JAVA_HOME
    fi
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v javac >/dev/null 2>&1; then
    JAVAC_PATH="$(command -v javac)"
    JAVAC_REAL_PATH=""
    if command -v readlink >/dev/null 2>&1; then
      RESOLVED="$(readlink -f "$JAVAC_PATH" 2>/dev/null || true)"
      if [[ -n "$RESOLVED" ]]; then
        JAVAC_REAL_PATH="$RESOLVED"
      fi
    elif command -v realpath >/dev/null 2>&1; then
      RESOLVED="$(realpath "$JAVAC_PATH" 2>/dev/null || true)"
      if [[ -n "$RESOLVED" ]]; then
        JAVAC_REAL_PATH="$RESOLVED"
      fi
    fi

    # Only trust javac-based inference when the canonical path is resolved.
    if [[ -n "$JAVAC_REAL_PATH" && "$JAVAC_REAL_PATH" == */bin/javac ]]; then
      CANDIDATE_JAVA_HOME="$(cd "$(dirname "$JAVAC_REAL_PATH")/.." && pwd -P)"
      if [[ "$CANDIDATE_JAVA_HOME" != "/usr" \
            && -x "$CANDIDATE_JAVA_HOME/bin/java" \
            && -x "$CANDIDATE_JAVA_HOME/bin/javac" ]] \
            && java_version_is_supported "$CANDIDATE_JAVA_HOME"; then
        JAVA_HOME="$CANDIDATE_JAVA_HOME"
        export JAVA_HOME
      fi
    fi
  fi
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
  echo "ERROR: JAVA_HOME is not set correctly."
  echo "Please set it before running this script."
  echo "Required Java version: ${required_java_major}+."
  echo "Note: javac fallback requires canonical-path resolution (readlink -f or realpath)."
  echo "Examples:"
  echo "  macOS: export JAVA_HOME=\$(/usr/libexec/java_home -v 21)"
  echo "  Linux: export JAVA_HOME=/path/to/jdk"
  exit 1
fi

exec mvn "$@"
