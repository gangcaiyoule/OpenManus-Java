#!/usr/bin/env bash
set -euo pipefail

DEFAULT_OPENAI_LIVE_MODELS=(
  "gpt-5.4"
  "gpt-5"
  "gpt-4o"
)

LIVE_SMOKE_TEMP_TRUSTSTORE=""

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

resolve_live_smoke_path() {
  local value
  value="$(trim "${1-}")"
  if [[ -z "$value" ]]; then
    printf '%s' ""
    return
  fi

  case "$value" in
    "~")
      if [[ -n "${HOME-}" ]]; then
        value="${HOME}"
      fi
      ;;
    "~/"*)
      if [[ -n "${HOME-}" ]]; then
        value="${HOME}${value:1}"
      fi
      ;;
    "\$HOME/"*)
      if [[ -n "${HOME-}" ]]; then
        value="${HOME}/${value#\$HOME/}"
      fi
      ;;
    "\${HOME}/"*)
      if [[ -n "${HOME-}" ]]; then
        value="${HOME}/${value#\$\{HOME\}/}"
      fi
      ;;
  esac

  printf '%s' "$value"
}

mask_api_key() {
  local value
  value="$(trim "$1")"
  if [[ -z "$value" ]]; then
    printf '%s' "<missing>"
    return
  fi

  local length=${#value}
  local suffix="$value"
  if (( length > 4 )); then
    suffix="${value: -4}"
  fi
  printf 'len=%d,suffix=%s' "$length" "$suffix"
}

print_effective_live_smoke_config() {
  local openai_candidates
  openai_candidates="$(collect_effective_openai_candidates)"
  local model_source
  local base_url_source
  local api_key_source
  local ca_cert_file
  local tls_client_protocols
  local https_protocols
  local extra_java_tool_options
  model_source="${OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_MODEL_SOURCE-unknown}"
  base_url_source="${OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_BASE_URL_SOURCE-unknown}"
  api_key_source="${OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_API_KEY_SOURCE-unknown}"
  ca_cert_file="$(resolve_live_smoke_path "${OPENMANUS_LIVE_CA_CERT_FILE-}")"
  tls_client_protocols="$(trim "${OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS-}")"
  https_protocols="$(trim "${OPENMANUS_LIVE_HTTPS_PROTOCOLS-}")"
  extra_java_tool_options="$(trim "${OPENMANUS_LIVE_JAVA_TOOL_OPTIONS-}")"

  echo "Live smoke effective OpenAI config: models=${openai_candidates}, baseUrl=$(trim "${OPENMANUS_LIVE_BASE_URL-}"), apiKey=$(mask_api_key "${OPENMANUS_LIVE_API_KEY-}"), caCertFile=${ca_cert_file:-<default-jvm-truststore>}, source=models:${model_source},baseUrl:${base_url_source},apiKey:${api_key_source}"
  if [[ -n "$tls_client_protocols" || -n "$https_protocols" || -n "$extra_java_tool_options" ]]; then
    echo "Live smoke JVM overrides: jdkTlsClientProtocols=${tls_client_protocols:-<default>}, httpsProtocols=${https_protocols:-<default>}, extraJavaToolOptions=$([[ -n "$extra_java_tool_options" ]] && printf '%s' '<provided>' || printf '%s' '<none>')"
  fi
}

append_unique_csv_value() {
  local current="$1"
  local candidate
  candidate="$(trim "$2")"
  if [[ -z "$candidate" ]]; then
    printf '%s' "$current"
    return
  fi
  if [[ -z "$current" ]]; then
    printf '%s' "$candidate"
    return
  fi
  if [[ ",$current," == *",$candidate,"* ]]; then
    printf '%s' "$current"
    return
  fi
  printf '%s,%s' "$current" "$candidate"
}

append_unique_csv_values() {
  local current="$1"
  local raw_values="$2"
  local part
  if [[ -z "$(trim "$raw_values")" ]]; then
    printf '%s' "$current"
    return
  fi
  IFS=',' read -r -a parts <<< "$raw_values"
  for part in "${parts[@]}"; do
    current="$(append_unique_csv_value "$current" "$part")"
  done
  printf '%s' "$current"
}

collect_effective_openai_candidates() {
  local merged=""
  merged="$(append_unique_csv_value "$merged" "${OPENMANUS_LIVE_MODEL-}")"
  merged="$(append_unique_csv_values "$merged" "${OPENMANUS_LIVE_MODEL_CANDIDATES-}")"
  if [[ -z "$merged" ]]; then
    local fallback_model
    for fallback_model in "${DEFAULT_OPENAI_LIVE_MODELS[@]}"; do
      merged="$(append_unique_csv_value "$merged" "$fallback_model")"
    done
  fi
  printf '%s' "$merged"
}

has_non_blank_value() {
  [[ -n "$(trim "$1")" ]]
}

detect_openai_model_source() {
  if has_non_blank_value "${OPENMANUS_LIVE_MODEL-}" || has_non_blank_value "${OPENMANUS_LIVE_MODEL_CANDIDATES-}"; then
    printf '%s' "live"
    return
  fi
  if has_non_blank_value "${OPENMANUS_LLM_DEFAULT_LLM_MODEL-}" || has_non_blank_value "${OPENMANUS_LLM_DEFAULT_LLM_MODEL_CANDIDATES-}"; then
    printf '%s' "default-llm"
    return
  fi
  if has_non_blank_value "${OPENAI_MODEL-}" || has_non_blank_value "${OPENAI_MODEL_CANDIDATES-}"; then
    printf '%s' "legacy-openai"
    return
  fi
  printf '%s' "built-in-fallback"
}

detect_openai_base_url_source() {
  if has_non_blank_value "${OPENMANUS_LIVE_BASE_URL-}"; then
    printf '%s' "live"
    return
  fi
  if has_non_blank_value "${OPENMANUS_LLM_DEFAULT_LLM_BASE_URL-}"; then
    printf '%s' "default-llm"
    return
  fi
  if has_non_blank_value "${OPENAI_BASE_URL-}"; then
    printf '%s' "legacy-openai"
    return
  fi
  printf '%s' "missing"
}

detect_openai_api_key_source() {
  if has_non_blank_value "${OPENMANUS_LIVE_API_KEY-}"; then
    printf '%s' "live"
    return
  fi
  if has_non_blank_value "${OPENMANUS_LLM_DEFAULT_LLM_API_KEY-}"; then
    printf '%s' "default-llm"
    return
  fi
  if has_non_blank_value "${OPENAI_API_KEY-}"; then
    printf '%s' "legacy-openai"
    return
  fi
  printf '%s' "missing"
}

capture_openai_live_smoke_sources() {
  export OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_MODEL_SOURCE
  export OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_BASE_URL_SOURCE
  export OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_API_KEY_SOURCE
  OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_MODEL_SOURCE="$(detect_openai_model_source)"
  OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_BASE_URL_SOURCE="$(detect_openai_base_url_source)"
  OPENMANUS_INTERNAL_LIVE_SMOKE_OPENAI_API_KEY_SOURCE="$(detect_openai_api_key_source)"
}

normalize_dotenv_value() {
  local raw_value value quote_char closing_index trailing
  raw_value="$(trim "$1")"
  if [[ -z "$raw_value" ]]; then
    printf '%s' ""
    return
  fi

  quote_char="${raw_value:0:1}"
  if [[ "$quote_char" == '"' || "$quote_char" == "'" ]]; then
    closing_index=1
    while [[ $closing_index -lt ${#raw_value} ]]; do
      if [[ "${raw_value:$closing_index:1}" == "$quote_char" ]]; then
        break
      fi
      closing_index=$((closing_index + 1))
    done
    if [[ $closing_index -lt ${#raw_value} ]]; then
      trailing="$(trim "${raw_value:$((closing_index + 1))}")"
      if [[ -z "$trailing" || "$trailing" == \#* ]]; then
        printf '%s' "${raw_value:1:$((closing_index - 1))}"
        return
      fi
    fi
  fi

  value="$raw_value"
  if [[ "$value" == \#* ]]; then
    printf '%s' ""
    return
  fi

  if [[ "$value" == *" #"* ]]; then
    value="$(trim "${value%% \#*}")"
  fi

  printf '%s' "$value"
}

load_dotenv_if_present() {
  local dotenv_path="$ROOT_DIR/.env"
  [[ -f "$dotenv_path" ]] || return 0

  while IFS= read -r raw_line || [[ -n "$raw_line" ]]; do
    local line
    line="$(trim "$raw_line")"
    [[ -z "$line" || "$line" == \#* ]] && continue
    if [[ "$line" == export\ * ]]; then
      line="$(trim "${line#export }")"
    fi

    [[ "$line" == *=* ]] || continue

    local key="${line%%=*}"
    local raw_value="${line#*=}"
    key="$(trim "$key")"
    [[ -n "$key" ]] || continue
    if [[ -n "$(trim "${!key-}")" ]]; then
      continue
    fi

    export "$key=$(normalize_dotenv_value "$raw_value")"
  done < "$dotenv_path"
}

backfill_openai_live_env_from_default_llm() {
  if [[ -z "$(trim "${OPENMANUS_LIVE_MODEL-}")" ]]; then
    export OPENMANUS_LIVE_MODEL="$(trim "${OPENMANUS_LLM_DEFAULT_LLM_MODEL-}")"
  fi
  if [[ -z "$(trim "${OPENMANUS_LIVE_BASE_URL-}")" ]]; then
    export OPENMANUS_LIVE_BASE_URL="$(trim "${OPENMANUS_LLM_DEFAULT_LLM_BASE_URL-}")"
  fi
  if [[ -z "$(trim "${OPENMANUS_LIVE_API_KEY-}")" ]]; then
    export OPENMANUS_LIVE_API_KEY="$(trim "${OPENMANUS_LLM_DEFAULT_LLM_API_KEY-}")"
  fi
  if [[ -z "$(trim "${OPENMANUS_LIVE_MODEL_CANDIDATES-}")" ]]; then
    export OPENMANUS_LIVE_MODEL_CANDIDATES="$(trim "${OPENMANUS_LLM_DEFAULT_LLM_MODEL_CANDIDATES-}")"
  fi
}

backfill_openai_live_env_from_legacy_openai() {
  if [[ -z "$(trim "${OPENMANUS_LIVE_MODEL-}")" ]]; then
    export OPENMANUS_LIVE_MODEL="$(trim "${OPENAI_MODEL-}")"
  fi
  if [[ -z "$(trim "${OPENMANUS_LIVE_BASE_URL-}")" ]]; then
    export OPENMANUS_LIVE_BASE_URL="$(trim "${OPENAI_BASE_URL-}")"
  fi
  if [[ -z "$(trim "${OPENMANUS_LIVE_API_KEY-}")" ]]; then
    export OPENMANUS_LIVE_API_KEY="$(trim "${OPENAI_API_KEY-}")"
  fi
  if [[ -z "$(trim "${OPENMANUS_LIVE_MODEL_CANDIDATES-}")" ]]; then
    export OPENMANUS_LIVE_MODEL_CANDIDATES="$(trim "${OPENAI_MODEL_CANDIDATES-}")"
  fi
}

backfill_provider_live_env_from_profile() {
  local live_prefix="$1"
  local profile_prefix="$2"

  local model_var="${live_prefix}_MODEL"
  local base_url_var="${live_prefix}_BASE_URL"
  local api_key_var="${live_prefix}_API_KEY"
  local profile_model_var="${profile_prefix}_MODEL"
  local profile_base_url_var="${profile_prefix}_BASE_URL"
  local profile_api_key_var="${profile_prefix}_API_KEY"

  if [[ -z "$(trim "${!model_var-}")" ]]; then
    export "${model_var}=$(trim "${!profile_model_var-}")"
  fi
  if [[ -z "$(trim "${!base_url_var-}")" ]]; then
    export "${base_url_var}=$(trim "${!profile_base_url_var-}")"
  fi
  if [[ -z "$(trim "${!api_key_var-}")" ]]; then
    export "${api_key_var}=$(trim "${!profile_api_key_var-}")"
  fi
}

collect_missing_openai_live_env_vars() {
  local required_vars=(
    OPENMANUS_LIVE_BASE_URL
    OPENMANUS_LIVE_API_KEY
  )
  local key

  for key in "${required_vars[@]}"; do
    local value
    value="$(trim "${!key-}")"
    if [[ -z "$value" ]]; then
      MISSING_LIVE_ENV_VARS+=("$key")
    fi
  done

}

collect_placeholder_live_env_var() {
  local key="$1"
  local value
  value="$(trim "${!key-}")"
  if [[ -n "$value" ]] && is_placeholder_live_env_var "$key" "$value"; then
    PLACEHOLDER_LIVE_ENV_VARS+=("$key")
  fi
}

collect_optional_provider_live_env() {
  local provider_name="$1"
  local live_prefix="$2"
  local test_name="$3"
  local fallback_hint="$4"

  local model_var="${live_prefix}_MODEL"
  local base_url_var="${live_prefix}_BASE_URL"
  local api_key_var="${live_prefix}_API_KEY"

  local model_value
  local base_url_value
  local api_key_value
  model_value="$(trim "${!model_var-}")"
  base_url_value="$(trim "${!base_url_var-}")"
  api_key_value="$(trim "${!api_key_var-}")"

  local configured_count=0
  [[ -n "$model_value" ]] && configured_count=$((configured_count + 1))
  [[ -n "$base_url_value" ]] && configured_count=$((configured_count + 1))
  [[ -n "$api_key_value" ]] && configured_count=$((configured_count + 1))

  if (( configured_count == 0 )); then
    return
  fi

  if (( configured_count < 3 )); then
    OPTIONAL_PROVIDER_ERRORS+=(
      "ERROR: incomplete ${provider_name} live smoke env vars: ${model_var} ${base_url_var} ${api_key_var}"
    )
    OPTIONAL_PROVIDER_FALLBACK_HINTS+=(
      "${provider_name} vars also accept provider profile fallback: ${fallback_hint}"
    )
    return
  fi

  collect_placeholder_live_env_var "$api_key_var"
  LIVE_SMOKE_TESTS+=("$test_name")
}

extract_xml_attribute() {
  local report="$1"
  local tag="$2"
  local attribute="$3"

  sed -n "s/.*<${tag}[^>]*${attribute}=\"\\([^\"]*\\)\".*/\\1/p" "$report" | head -n 1
}

xml_unescape() {
  local value="$1"
  value="${value//&lt;/<}"
  value="${value//&gt;/>}"
  value="${value//&quot;/\"}"
  value="${value//&apos;/\'}"
  value="${value//&amp;/&}"
  printf '%s' "$value"
}

extract_xml_body() {
  local report="$1"
  local tag="$2"

  awk -v tag="$tag" '
    BEGIN { capture = 0 }
    {
      line = $0
      if (!capture && line ~ ("<" tag "[ >]")) {
        capture = 1
        sub(".*<" tag "[^>]*>", "", line)
      }
      if (capture) {
        if (line ~ ("</" tag ">")) {
          sub("</" tag ">.*", "", line)
          printf "%s ", line
          exit
        }
        printf "%s ", line
      }
    }
  ' "$report" \
    | sed 's/<!\[CDATA\[//g; s/\]\]>//g; s/[[:space:]][[:space:]]*/ /g; s/^ //; s/ $//'
}

extract_first_report_issue_detail() {
  local report="$1"
  local tag="$2"
  local detail

  detail="$(extract_xml_attribute "$report" "$tag" "message")"
  if [[ -n "$detail" ]]; then
    xml_unescape "$detail"
    return
  fi

  detail="$(extract_xml_body "$report" "$tag")"
  if [[ -n "$detail" ]]; then
    xml_unescape "$detail"
  fi
}

is_tls_environment_issue_detail() {
  local detail
  detail="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  [[ "$detail" == *"pkix path building failed"* ]] \
    || [[ "$detail" == *"unable to find valid certification path"* ]] \
    || [[ "$detail" == *"sun.security.provider.certpath"* ]] \
    || [[ "$detail" == *"certificate_unknown"* ]] \
    || [[ "$detail" == *"remote host terminated the handshake"* ]] \
    || [[ "$detail" == *"unable to parse tls packet header"* ]] \
    || [[ "$detail" == *"handshake_failure"* ]] \
    || [[ "$detail" == *"handshake alert"* ]] \
    || [[ "$detail" == *"peer not authenticated"* ]] \
    || [[ "$detail" == *"sslhandshakeexception"* ]] \
    || [[ "$detail" == *"sslexception"* ]]
}

print_live_smoke_issue_guidance() {
  local issue="$1"
  local detail="$2"
  if [[ "$issue" == "Assumption failed" ]] && is_tls_environment_issue_detail "$detail"; then
    echo "Live smoke remediation: TLS/certificate handshake issue detected. Provide OPENMANUS_LIVE_CA_CERT_FILE with the gateway CA bundle, or set OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS / OPENMANUS_LIVE_HTTPS_PROTOCOLS / OPENMANUS_LIVE_JAVA_TOOL_OPTIONS for the live smoke process only."
  fi
}

print_live_env_recovery_guidance() {
  local missing_vars=("$@")
  local needs_openai_fallback=false
  local needs_anthropic_fallback=false
  local needs_gemini_fallback=false
  local key

  for key in "${missing_vars[@]}"; do
    case "$key" in
      OPENMANUS_LIVE_MODEL|OPENMANUS_LIVE_BASE_URL|OPENMANUS_LIVE_API_KEY)
        needs_openai_fallback=true
        ;;
      OPENMANUS_LIVE_ANTHROPIC_MODEL|OPENMANUS_LIVE_ANTHROPIC_BASE_URL|OPENMANUS_LIVE_ANTHROPIC_API_KEY)
        needs_anthropic_fallback=true
        ;;
      OPENMANUS_LIVE_GEMINI_MODEL|OPENMANUS_LIVE_GEMINI_BASE_URL|OPENMANUS_LIVE_GEMINI_API_KEY)
        needs_gemini_fallback=true
        ;;
    esac
  done

  echo "Please set them in the current shell or repository root .env before running live smoke."
  if [[ "$needs_openai_fallback" == true ]]; then
    echo "OpenAI-compatible model selection also accepts candidate fallback: OPENMANUS_LIVE_MODEL_CANDIDATES OPENMANUS_LLM_DEFAULT_LLM_MODEL_CANDIDATES OPENAI_MODEL_CANDIDATES"
    echo "OpenAI-compatible vars also accept default LLM fallback: OPENMANUS_LLM_DEFAULT_LLM_MODEL OPENMANUS_LLM_DEFAULT_LLM_BASE_URL OPENMANUS_LLM_DEFAULT_LLM_API_KEY"
    echo "OpenAI-compatible vars also accept legacy fallback: OPENAI_MODEL OPENAI_BASE_URL OPENAI_API_KEY"
  fi
  if [[ "$needs_anthropic_fallback" == true ]]; then
    echo "Anthropic vars also accept provider profile fallback: OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY"
  fi
  if [[ "$needs_gemini_fallback" == true ]]; then
    echo "Gemini vars also accept provider profile fallback: OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY"
  fi
}

is_placeholder_live_env_var() {
  local key="$1"
  local value="$2"

  case "$key" in
    OPENMANUS_LIVE_API_KEY)
      [[ "$value" == "your-openai-live-api-key-here" || "$value" == "your-openai-compatible-api-key-here" ]]
      ;;
    OPENMANUS_LIVE_ANTHROPIC_API_KEY)
      [[ "$value" == "your-anthropic-live-api-key-here" || "$value" == "your-anthropic-api-key-here" ]]
      ;;
    OPENMANUS_LIVE_GEMINI_API_KEY)
      [[ "$value" == "your-gemini-live-api-key-here" || "$value" == "your-gemini-api-key-here" ]]
      ;;
    *)
      return 1
      ;;
  esac
}

cleanup_live_smoke_temp_truststore() {
  if [[ -n "$LIVE_SMOKE_TEMP_TRUSTSTORE" && -f "$LIVE_SMOKE_TEMP_TRUSTSTORE" ]]; then
    rm -f "$LIVE_SMOKE_TEMP_TRUSTSTORE"
  fi
}

append_java_option() {
  local current_value="$1"
  local option="$2"
  if [[ -z "$current_value" ]]; then
    printf '%s' "$option"
    return
  fi
  printf '%s %s' "$current_value" "$option"
}

import_live_smoke_ca_bundle() {
  local cert_file="$1"
  local truststore_path="$2"
  local cert_dir
  cert_dir="$(dirname "$truststore_path")/live-smoke-certs"
  mkdir -p "$cert_dir"

  awk '
    BEGIN { inside = 0; count = 0 }
    /-----BEGIN CERTIFICATE-----/ {
      inside = 1
      count++
      file = sprintf("%s/cert-%03d.pem", dir, count)
      print > file
      next
    }
    {
      if (inside) {
        print > file
      }
    }
    /-----END CERTIFICATE-----/ {
      inside = 0
      close(file)
    }
  ' dir="$cert_dir" "$cert_file"

  if [[ ! -d "$cert_dir" ]]; then
    echo "ERROR: OPENMANUS_LIVE_CA_CERT_FILE does not contain any PEM certificate block: $cert_file"
    exit 1
  fi

  shopt -s nullglob
  local cert_paths=("$cert_dir"/*.pem)
  shopt -u nullglob
  if [[ ${#cert_paths[@]} -eq 0 ]]; then
    echo "ERROR: OPENMANUS_LIVE_CA_CERT_FILE does not contain any PEM certificate block: $cert_file"
    rm -rf "$cert_dir"
    exit 1
  fi

  local index=1
  local cert_path
  for cert_path in "${cert_paths[@]}"; do
    if ! keytool -importcert \
      -noprompt \
      -storetype PKCS12 \
      -keystore "$truststore_path" \
      -storepass changeit \
      -alias "live-smoke-cert-${index}" \
      -file "$cert_path" >/dev/null 2>&1; then
      echo "ERROR: failed to import OPENMANUS_LIVE_CA_CERT_FILE into temporary truststore: $cert_file"
      rm -rf "$cert_dir"
      exit 1
    fi
    index=$((index + 1))
  done

  rm -rf "$cert_dir"
}

configure_live_smoke_truststore() {
  local cert_file
  cert_file="$(resolve_live_smoke_path "${OPENMANUS_LIVE_CA_CERT_FILE-}")"
  if [[ -z "$cert_file" ]]; then
    return
  fi
  if [[ ! -f "$cert_file" ]]; then
    echo "ERROR: OPENMANUS_LIVE_CA_CERT_FILE does not exist: $cert_file"
    exit 1
  fi
  if ! command -v keytool >/dev/null 2>&1; then
    echo "ERROR: keytool is required when OPENMANUS_LIVE_CA_CERT_FILE is set."
    exit 1
  fi

  LIVE_SMOKE_TEMP_TRUSTSTORE="$(mktemp "${TMPDIR:-/tmp}/openmanus-live-smoke-truststore.XXXXXX")"
  trap cleanup_live_smoke_temp_truststore EXIT
  rm -f "$LIVE_SMOKE_TEMP_TRUSTSTORE"
  import_live_smoke_ca_bundle "$cert_file" "$LIVE_SMOKE_TEMP_TRUSTSTORE"

  export JAVA_TOOL_OPTIONS
  JAVA_TOOL_OPTIONS="$(append_java_option "${JAVA_TOOL_OPTIONS-}" "-Djavax.net.ssl.trustStore=$LIVE_SMOKE_TEMP_TRUSTSTORE")"
  JAVA_TOOL_OPTIONS="$(append_java_option "$JAVA_TOOL_OPTIONS" "-Djavax.net.ssl.trustStoreType=PKCS12")"
  JAVA_TOOL_OPTIONS="$(append_java_option "$JAVA_TOOL_OPTIONS" "-Djavax.net.ssl.trustStorePassword=changeit")"
}

configure_live_smoke_jvm_overrides() {
  local tls_client_protocols
  local https_protocols
  local extra_java_tool_options
  tls_client_protocols="$(trim "${OPENMANUS_LIVE_JDK_TLS_CLIENT_PROTOCOLS-}")"
  https_protocols="$(trim "${OPENMANUS_LIVE_HTTPS_PROTOCOLS-}")"
  extra_java_tool_options="$(trim "${OPENMANUS_LIVE_JAVA_TOOL_OPTIONS-}")"

  if [[ -n "$tls_client_protocols" ]]; then
    export JAVA_TOOL_OPTIONS
    JAVA_TOOL_OPTIONS="$(append_java_option "${JAVA_TOOL_OPTIONS-}" "-Djdk.tls.client.protocols=$tls_client_protocols")"
  fi
  if [[ -n "$https_protocols" ]]; then
    export JAVA_TOOL_OPTIONS
    JAVA_TOOL_OPTIONS="$(append_java_option "${JAVA_TOOL_OPTIONS-}" "-Dhttps.protocols=$https_protocols")"
  fi
  if [[ -n "$extra_java_tool_options" ]]; then
    export JAVA_TOOL_OPTIONS
    JAVA_TOOL_OPTIONS="$(append_java_option "${JAVA_TOOL_OPTIONS-}" "$extra_java_tool_options")"
  fi
}

if [[ ! -x "./scripts/mvnw-local.sh" ]]; then
  echo "ERROR: ./scripts/mvnw-local.sh is not executable."
  echo "Please run: chmod +x scripts/mvnw-local.sh scripts/run-live-smoke.sh"
  exit 1
fi

load_dotenv_if_present
capture_openai_live_smoke_sources
backfill_openai_live_env_from_default_llm
backfill_openai_live_env_from_legacy_openai
backfill_provider_live_env_from_profile "OPENMANUS_LIVE_ANTHROPIC" "OPENMANUS_LLM_PROVIDERS_ANTHROPIC"
backfill_provider_live_env_from_profile "OPENMANUS_LIVE_GEMINI" "OPENMANUS_LLM_PROVIDERS_GEMINI"
configure_live_smoke_truststore
configure_live_smoke_jvm_overrides

MISSING_LIVE_ENV_VARS=()
PLACEHOLDER_LIVE_ENV_VARS=()
OPTIONAL_PROVIDER_ERRORS=()
OPTIONAL_PROVIDER_FALLBACK_HINTS=()

LIVE_SMOKE_TESTS=(
  OpenAiClientLiveSmokeTest
)

collect_missing_openai_live_env_vars
if [[ ${#MISSING_LIVE_ENV_VARS[@]} -gt 0 ]]; then
  echo "ERROR: missing live smoke env vars: ${MISSING_LIVE_ENV_VARS[*]}"
  print_live_env_recovery_guidance "${MISSING_LIVE_ENV_VARS[@]}"
  exit 1
fi

collect_placeholder_live_env_var "OPENMANUS_LIVE_API_KEY"

collect_optional_provider_live_env \
  "anthropic" \
  "OPENMANUS_LIVE_ANTHROPIC" \
  "AnthropicClientLiveSmokeTest" \
  "OPENMANUS_LLM_PROVIDERS_ANTHROPIC_MODEL OPENMANUS_LLM_PROVIDERS_ANTHROPIC_BASE_URL OPENMANUS_LLM_PROVIDERS_ANTHROPIC_API_KEY"

collect_optional_provider_live_env \
  "gemini" \
  "OPENMANUS_LIVE_GEMINI" \
  "GeminiClientLiveSmokeTest" \
  "OPENMANUS_LLM_PROVIDERS_GEMINI_MODEL OPENMANUS_LLM_PROVIDERS_GEMINI_BASE_URL OPENMANUS_LLM_PROVIDERS_GEMINI_API_KEY"

if [[ ${#OPTIONAL_PROVIDER_ERRORS[@]} -gt 0 ]]; then
  printf '%s\n' "${OPTIONAL_PROVIDER_ERRORS[@]}"
  echo "Please either unset the optional live smoke env entirely or provide all three vars."
  printf '%s\n' "${OPTIONAL_PROVIDER_FALLBACK_HINTS[@]}"
  exit 1
fi

if [[ ${#PLACEHOLDER_LIVE_ENV_VARS[@]} -gt 0 ]]; then
  echo "ERROR: placeholder live smoke env vars: ${PLACEHOLDER_LIVE_ENV_VARS[*]}"
  echo "Please replace dotenv.example placeholder values with real provider credentials before running live smoke."
  exit 1
fi

print_effective_live_smoke_config

LIVE_SMOKE_TEST_ARG="$(IFS=,; printf '%s' "${LIVE_SMOKE_TESTS[*]}")"

rm -f target/surefire-reports/TEST-*LiveSmokeTest.xml

set +e
./scripts/mvnw-local.sh -q -DskipITs test -Dgroups=live-smoke -Dopenmanus.liveSmoke.enabled=true -Dsurefire.excludedGroups= -Dtest="${LIVE_SMOKE_TEST_ARG}"
MVN_EXIT=$?
set -e

shopt -s nullglob
REPORT_FILES=(target/surefire-reports/TEST-*LiveSmokeTest.xml)
shopt -u nullglob

if [[ ${#REPORT_FILES[@]} -eq 0 ]]; then
  echo "ERROR: no live smoke surefire reports found under target/surefire-reports."
  exit 1
fi

total_tests=0
total_failures=0
total_errors=0
total_skipped=0
first_issue="none"
first_issue_detail=""

for report in "${REPORT_FILES[@]}"; do
  tests="$(sed -n 's/.*tests="\([0-9][0-9]*\)".*/\1/p' "$report" | head -n 1)"
  failures="$(sed -n 's/.*failures="\([0-9][0-9]*\)".*/\1/p' "$report" | head -n 1)"
  errors="$(sed -n 's/.*errors="\([0-9][0-9]*\)".*/\1/p' "$report" | head -n 1)"
  skipped="$(sed -n 's/.*skipped="\([0-9][0-9]*\)".*/\1/p' "$report" | head -n 1)"

  total_tests=$((total_tests + ${tests:-0}))
  total_failures=$((total_failures + ${failures:-0}))
  total_errors=$((total_errors + ${errors:-0}))
  total_skipped=$((total_skipped + ${skipped:-0}))

  if [[ "$first_issue" == "none" ]]; then
    if grep -q "<failure" "$report"; then
      first_issue="failure"
      first_issue_detail="$(extract_first_report_issue_detail "$report" "failure")"
    elif grep -q "<error" "$report"; then
      first_issue="error"
      first_issue_detail="$(extract_first_report_issue_detail "$report" "error")"
    elif grep -q "<skipped" "$report"; then
      if grep -q "Assumption failed" "$report"; then
        first_issue="Assumption failed"
      else
        first_issue="skipped"
      fi
      first_issue_detail="$(extract_first_report_issue_detail "$report" "skipped")"
    fi
  fi
done

echo "Live smoke summary: tests=${total_tests}, failures=${total_failures}, errors=${total_errors}, skipped=${total_skipped}"
echo "Live smoke first issue: ${first_issue}"
if [[ -n "$first_issue_detail" ]]; then
  echo "Live smoke first issue detail: ${first_issue_detail}"
fi
print_live_smoke_issue_guidance "$first_issue" "$first_issue_detail"

if (( total_failures > 0 || total_errors > 0 )); then
  exit 1
fi

if (( total_skipped > 0 )); then
  if [[ "$first_issue" == "Assumption failed" ]]; then
    exit 0
  fi
  exit 1
fi

exit "$MVN_EXIT"
