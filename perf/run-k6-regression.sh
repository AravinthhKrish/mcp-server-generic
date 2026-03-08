#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULT_DIR="${RESULT_DIR:-perf/results}"
SUMMARY_FILE="${SUMMARY_FILE:-$RESULT_DIR/mcp-regression-summary.json}"
LOG_FILE="${LOG_FILE:-$RESULT_DIR/k6-regression-run.log}"

mkdir -p "$RESULT_DIR"

{
  echo "# k6 regression execution"
  echo "timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo

  if ! command -v k6 >/dev/null 2>&1; then
    echo "ERROR: k6 is not installed or not in PATH"
    exit 127
  fi

  echo "$ k6 run perf/mcp-regression.k6.js --summary-export $SUMMARY_FILE"
  BASE_URL="$BASE_URL" k6 run perf/mcp-regression.k6.js --summary-export "$SUMMARY_FILE"
} | tee "$LOG_FILE"
