# mcp-server-generic

Kotlin/Spring starter implementation of a **single Data Access MCP server** with thin MCP tool handlers and pluggable domain adapters.

## Architecture (4 layers)

1. **MCP layer**
   * Tool endpoints (phase-1):
     * `drive.search_files`
     * `gmail.search_messages`
     * `news.search_articles`
     * `market.quote`
   * Resource endpoints:
     * `resource://news/sources`
     * `resource://system/provider-health`
2. **Domain adapters**
   * `GoogleDriveAdapter`
   * `GmailAdapter`
   * `NewsAdapter`
   * `MarketDataAdapter`
3. **Auth layer**
   * Access context and token store abstractions.
4. **Caching/event layer**
   * Cache service abstraction (in-memory implementation for MVP).

## Implementation plan

### Phase 1 (implemented)

* Bootstrap Kotlin + Spring Boot app.
* Add normalized domain models:
  * `DriveFile`, `MailMessage`, `Article`, `Quote`.
* Add tool contracts and handlers for:
  * `drive.search_files`
  * `gmail.search_messages`
  * `news.search_articles`
  * `market.quote`
* Add adapter interfaces and stub implementations.
* Add short-lived cache for quote responses.
* Add basic integration test for `market.quote`.

### Phase 2 (next)

* Add `drive.read_file_text`, `gmail.get_thread`, `market.quotes_batch`, `news.get_topic_digest`.
* Add pagination token persistence and stronger result freshness metadata.
* Add Redis-backed cache implementation.

### Phase 3 (next)

* Drive/Gmail watch integrations.
* Market WebSocket stream consumers.
* Provider routing + failover policy.
* Source registry persistence and article dedup pipeline.

## Package structure

```text
src/main/kotlin/com/example/mcp
├── auth
├── cache
├── domain
│   ├── drive
│   ├── gmail
│   ├── market
│   └── news
└── mcp
```

## Run

```bash
./gradlew bootRun
```

## Sample calls

```bash
curl -X POST http://localhost:8080/mcp/tools/market.quote \
  -H 'content-type: application/json' \
  -d '{"symbol":"AAPL"}'

curl http://localhost:8080/mcp/resources/news/sources
```
