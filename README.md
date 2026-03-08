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


## Gmail real-API mode

By default, Gmail uses a stub adapter. To connect to live Gmail data, enable Gmail integration and provide an OAuth bearer token:

```yaml
integrations:
  gmail:
    enabled: true
    base-url: https://gmail.googleapis.com/gmail/v1
    user-id: me
    access-token: ${GMAIL_ACCESS_TOKEN}
```

When enabled, `gmail.search_messages` calls:
- `GET /users/{userId}/messages`
- `GET /users/{userId}/messages/{messageId}?format=metadata`

and normalizes results into `MailMessage`.

## News multi-source real-API mode

By default, News uses a stub adapter. To aggregate multiple live sources in a single request, enable News integration and configure `integrations.news.sources`.

```yaml
integrations:
  news:
    enabled: true
    sources:
      - id: pinterest
        url: https://www.pinterest.com/news.rss
        type: RSS
        auth: NONE
      - id: toi
        url: https://timesofindia.indiatimes.com/rssfeedstopstories.cms
        type: RSS
        auth: NONE
      - id: github
        url: https://api.github.com/repos/spring-projects/spring-boot/issues
        type: JSON
        auth: HEADER
        auth-header: Authorization
        auth-token: ${GITHUB_TOKEN}
        query-param: q
```

`news.search_articles` can then query across all enabled sources (or a subset via `sources[]` in the tool input) in one request.

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
