#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULT_DIR="${RESULT_DIR:-perf/results}"
SUMMARY_FILE="${SUMMARY_FILE:-$RESULT_DIR/mcp-regression-summary.json}"
HTML_REPORT_FILE="${HTML_REPORT_FILE:-$RESULT_DIR/mcp-regression-report.html}"
LOG_FILE="${LOG_FILE:-$RESULT_DIR/k6-regression-run.log}"
MCP_API_TOKEN="${MCP_API_TOKEN:-dev-token}"

mkdir -p "$RESULT_DIR"

{
  echo "# k6 regression execution"
  echo "timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo

  if ! command -v k6 >/dev/null 2>&1; then
    echo "ERROR: k6 is not installed or not in PATH"
    exit 127
  fi

  echo "$ BASE_URL=$BASE_URL MCP_API_TOKEN=$MCP_API_TOKEN SUMMARY_FILE=$SUMMARY_FILE HTML_REPORT_FILE=$HTML_REPORT_FILE k6 run perf/mcp-regression.k6.js"
  BASE_URL="$BASE_URL" MCP_API_TOKEN="$MCP_API_TOKEN" SUMMARY_FILE="$SUMMARY_FILE" HTML_REPORT_FILE="$HTML_REPORT_FILE" k6 run perf/mcp-regression.k6.js

  echo "JSON summary generated at: $SUMMARY_FILE"
  echo "HTML report generated at: $HTML_REPORT_FILE"
} | tee "$LOG_FILE"
