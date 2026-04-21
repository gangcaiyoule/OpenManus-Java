#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -n "${JAVA_HOME:-}" ]]; then
  if [[ ! -x "${JAVA_HOME}/bin/java" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
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
            && -x "$CANDIDATE_JAVA_HOME/bin/javac" ]]; then
        JAVA_HOME="$CANDIDATE_JAVA_HOME"
        export JAVA_HOME
      fi
    fi
  fi
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
  echo "ERROR: JAVA_HOME is not set correctly."
  echo "Please set it before running this script."
  echo "Note: javac fallback requires canonical-path resolution (readlink -f or realpath)."
  echo "Examples:"
  echo "  macOS: export JAVA_HOME=\$(/usr/libexec/java_home -v 21)"
  echo "  Linux: export JAVA_HOME=/path/to/jdk"
  exit 1
fi

exec mvn "$@"
