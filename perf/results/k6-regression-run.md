# k6 regression execution result

Timestamp (UTC): 2026-03-08T07:23:15Z

## Commands executed

```bash
./perf/run-k6-regression.sh
```

## Outcome

1. k6 execution could not start because `k6` is not installed in this environment (`command not found`).
2. Because k6 did not run, `perf/results/mcp-regression-summary.json` and `perf/results/mcp-regression-report.html` were not generated for this run.

## Raw output excerpt

```text
# k6 regression execution
timestamp: 2026-03-08T07:23:15Z

ERROR: k6 is not installed or not in PATH
```
