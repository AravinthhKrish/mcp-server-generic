# mcp-server-generic

Kotlin and Spring WebFlux data-access MCP server with standard streamable HTTP,
authenticated REST compatibility endpoints, provider adapters, durable upload
workflows, and deterministic evaluation gates.

## MCP endpoint

The standard MCP endpoint is `POST /mcp`. It supports initialization, sessions,
`tools/list`, and `tools/call` through the official Java MCP SDK. Send the server
token in every request:

```http
Authorization: Bearer ${MCP_API_TOKEN}
Content-Type: application/json
Accept: application/json, text/event-stream
```

REST compatibility routes are available at `/mcp/tools/*`, `/mcp/resources/*`,
and `/api/mcp/*`. They require the same bearer token.

## Tools

Drive:

- `drive.search_files`
- `drive.create_folder`
- `drive.upload_file`
- `drive.file_metadata`
- `drive.read_file_text`
- `drive.start_resumable_upload`
- `drive.upload_chunk`
- `drive.upload_status`
- `drive.start_reel_upload`
- `drive.upload_reel_chunk`
- `drive.reel_upload_status`

Other providers:

- `gmail.search_messages`
- `gmail.get_thread`
- `news.search_articles`
- `news.get_topic_digest`
- `web_search`
- `market.quote`
- `market.quotes_batch`

The reel workflow creates an optional folder, starts a resumable Drive session,
accepts bounded chunks, persists confirmed byte progress, and returns final file
metadata. `idempotencyKey` prevents duplicate jobs when a caller retries start.

## Run

```bash
MCP_API_TOKEN=replace-me ./gradlew bootRun
```

Stub adapters are used when integrations are disabled. Never use the default
development token outside local development.

## Live Google Drive and OAuth

Configure direct-token Drive access:

```yaml
integrations:
  drive:
    enabled: true
    access-token: ${GOOGLE_DRIVE_ACCESS_TOKEN}
```

For per-user OAuth consent and automatic refresh, configure:

```yaml
integrations:
  google-oauth:
    enabled: true
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    redirect-uri: https://your-host.example/oauth/google/callback
```

Start consent with authenticated `POST /api/mcp/oauth/google/authorize` using
`tenantId` and `userId`. The callback stores tenant-isolated token references.
Drive tools can resolve those credentials by receiving the same `tenantId` and
`userId`; an explicit `accessToken` still overrides stored/configured tokens.

## Persistence

Production deployments should enable all stores and provide an AES-256 key:

```yaml
storage:
  tokens:
    enabled: true
    path: /var/lib/mcp/oauth-token-references.json
  secrets:
    enabled: true
    path: /var/lib/mcp/oauth-secrets.json
    encryption-key-base64: ${MCP_SECRET_ENCRYPTION_KEY_BASE64}
  cache:
    enabled: true
    path: /var/lib/mcp/cache.json
  jobs:
    enabled: true
    path: /var/lib/mcp/reel-upload-jobs.json
  upload-sessions:
    enabled: true
    path: /var/lib/mcp/drive-upload-sessions.json
```

Secret values are AES-GCM encrypted. Token references, cache entries, reel jobs,
and provider upload sessions use atomic file replacement. Use durable mounted
storage and restrict filesystem permissions to the service account.

## Evaluation

```bash
./gradlew evaluate
```

This runs all tests, enforces coverage thresholds, and generates reports under
`build/reports/`. CI runs the same gate and uploads its evidence. For an HTTP
load/regression pass with the service running:

```bash
k6 run perf/mcp-regression.k6.js
```

See [EVALUATION.md](EVALUATION.md) for the evaluation matrix and live-provider
prerequisites.
