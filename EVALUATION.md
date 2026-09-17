# MCP evaluation framework

Run the deterministic evaluation gate with:

```bash
./gradlew evaluate
```

The gate compiles production and test code, runs all JUnit tests, verifies at
least 60% line and 25% branch coverage, and generates HTML/XML JaCoCo reports.
CI runs the same command and retains test, result, and coverage artifacts.

## Evidence matrix

| Area | Evaluation |
|---|---|
| Authentication | Unauthorized REST, resource, and MCP requests are rejected |
| MCP protocol | Initialize, initialized notification, session, tools/list, schema, tools/call |
| Drive upload contract | multipart/related request, metadata ordering, resumable initiation and Content-Range |
| Reliability | timeouts, 429 retry, invalid base64, offset mismatch, bounded chunks |
| OAuth | one-time consent state, code exchange, refresh, tenant isolation, encrypted persistence |
| Recovery | token refs, secrets, cache, provider sessions, and reel jobs survive store recreation |
| Phase 2 tools | Drive text, Gmail thread, quote batch, topic digest response behavior |
| Workflow | idempotent reel start, chunk progress, completion, metadata, status recovery |

For an HTTP load/regression pass, start the application in stub mode and run:

```bash
k6 run perf/mcp-regression.k6.js
```

Live Google evaluation additionally requires OAuth client credentials, a test
Google account, Drive API access, and persistent storage settings. Automated
tests use enforcing local provider fixtures and never require or expose live
credentials.
