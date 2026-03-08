package com.example.mcp.domain.news

import com.example.mcp.domain.Article
import com.example.mcp.mcp.NewsSearchArticlesInput
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.reactive.function.client.WebClient
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

interface NewsAdapter {
    fun searchArticles(input: NewsSearchArticlesInput): List<Article>
}

@Component
@ConditionalOnProperty(prefix = "integrations.news", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class StubNewsAdapter : NewsAdapter {
    override fun searchArticles(input: NewsSearchArticlesInput): List<Article> {
        return listOf(
            Article(
                id = "art_001",
                source = "reuters",
                title = "Markets open higher on earnings momentum",
                url = "https://example.com/news/markets-open-higher",
                publishedAt = Instant.now(),
                author = "News Desk",
                summary = "Global indices moved higher on better-than-expected earnings.",
                tags = listOf("markets", "earnings")
            )
        )
    }
}

@Component
@ConditionalOnProperty(prefix = "integrations.news", name = ["enabled"], havingValue = "true")
class ApiNewsAdapter(
    private val properties: NewsProperties,
    private val objectMapper: ObjectMapper,
    @Qualifier("newsWebClient") private val newsWebClient: WebClient
) : NewsAdapter {
    private val logger = LoggerFactory.getLogger(ApiNewsAdapter::class.java)

    override fun searchArticles(input: NewsSearchArticlesInput): List<Article> {
        val selectedSources = properties.sources
            .asSequence()
            .filter { it.enabled }
            .filter { input.sources.isEmpty() || input.sources.contains(it.id) }
            .filter(::isAllowedSource)
            .toList()

        val aggregated = selectedSources.flatMap { source ->
            fetchWithIsolation(source, input)
        }

        return aggregated
            .filter { matchesQuery(it, input.query) }
            .filter { input.from == null || !it.publishedAt.isBefore(input.from) }
            .filter { input.to == null || !it.publishedAt.isAfter(input.to) }
            .sortedByDescending(Article::publishedAt)
            .take(input.limit)
    }

    private fun isAllowedSource(source: NewsSourceConfig): Boolean {
        val host = runCatching { URI.create(source.url).host?.lowercase().orEmpty() }.getOrDefault("")
        if (host.isBlank()) {
            logger.warn("Skipping news source '{}' due to invalid host in url='{}'", source.id, source.url)
            return false
        }

        if (properties.blockedHosts.any { host.contains(it.lowercase()) }) {
            logger.warn("Skipping news source '{}' because host '{}' is blocked", source.id, host)
            return false
        }

        if (properties.allowedHosts.isNotEmpty() && properties.allowedHosts.none { host.contains(it.lowercase()) }) {
            logger.warn("Skipping news source '{}' because host '{}' is not in allow-list", source.id, host)
            return false
        }

        return true
    }

    private fun fetchWithIsolation(source: NewsSourceConfig, input: NewsSearchArticlesInput): List<Article> {
        val host = runCatching { URI.create(source.url).host ?: "unknown" }.getOrDefault("unknown")

        var lastException: Exception? = null
        repeat(properties.maxRetries + 1) { attempt ->
            try {
                val articles = fetchFromSource(source, input)
                logger.info("news source completed source={} host={} articles={}", source.id, host, articles.size)
                return articles
            } catch (ex: Exception) {
                lastException = ex
                val transient = isTransientException(ex)
                logger.warn(
                    "news source failed source={} host={} attempt={} transient={} error={}",
                    source.id,
                    host,
                    attempt + 1,
                    transient,
                    ex.toString()
                )
                if (!transient || attempt >= properties.maxRetries) {
                    break
                }
                Thread.sleep(300L * (attempt + 1))
            }
        }

        logger.warn("news source exhausted source={} host={} returning empty result", source.id, host, lastException)
        return emptyList()
    }

    private fun isTransientException(ex: Exception): Boolean {
        val message = ex.toString().lowercase()
        return ex is ResourceAccessException ||
            message.contains("timeout") ||
            message.contains("prematureclose") ||
            message.contains("connection reset") ||
            message.contains("temporarily unavailable")
    }

    private fun fetchFromSource(source: NewsSourceConfig, input: NewsSearchArticlesInput): List<Article> {
        val responseBody = newsWebClient.get()
            .uri(buildUri(source, input.query))
            .headers { headers -> addAuthHeaders(headers, source) }
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: return emptyList()

        return when (source.type) {
            SourceType.RSS, SourceType.ATOM -> parseXmlFeed(responseBody, source.id)
            SourceType.JSON -> parseJsonFeed(responseBody, source.id)
        }
    }

    private fun buildUri(source: NewsSourceConfig, query: String): URI {
        val base = URI.create(source.url)
        val queryParam = source.queryParam ?: return base
        val separator = if (base.query.isNullOrBlank()) "?" else "&"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        return URI.create("${source.url}$separator$queryParam=$encodedQuery")
    }

    private fun addAuthHeaders(headers: org.springframework.http.HttpHeaders, source: NewsSourceConfig) {
        when (source.auth) {
            SourceAuth.NONE -> Unit
            SourceAuth.BEARER -> {
                if (source.authToken.isNotBlank()) {
                    headers.setBearerAuth(source.authToken)
                }
            }
            SourceAuth.HEADER -> {
                if (source.authToken.isNotBlank()) {
                    headers.set(source.authHeader, source.authToken)
                }
            }
        }
    }

    private fun parseXmlFeed(body: String, sourceId: String): List<Article> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(body)))

        val rssItems = document.getElementsByTagName("item")
        if (rssItems.length > 0) {
            return (0 until rssItems.length).mapNotNull { idx ->
                (rssItems.item(idx) as? Element)?.let { parseRssItem(it, sourceId) }
            }
        }

        val atomEntries = document.getElementsByTagName("entry")
        return (0 until atomEntries.length).mapNotNull { idx ->
            (atomEntries.item(idx) as? Element)?.let { parseAtomEntry(it, sourceId) }
        }
    }

    private fun parseRssItem(item: Element, sourceId: String): Article {
        val title = childText(item, "title") ?: ""
        val link = childText(item, "link") ?: ""
        val publishedAt = parseDate(childText(item, "pubDate"))
        val author = childText(item, "author")
        val summary = childText(item, "description")

        return Article(
            id = "$sourceId:${link.ifBlank { title }}",
            source = sourceId,
            title = title,
            url = link,
            publishedAt = publishedAt,
            author = author,
            summary = summary,
            tags = emptyList()
        )
    }

    private fun parseAtomEntry(entry: Element, sourceId: String): Article {
        val title = childText(entry, "title") ?: ""
        val link = firstElementByTag(entry, "link")?.getAttribute("href") ?: ""
        val publishedAt = parseDate(childText(entry, "updated") ?: childText(entry, "published"))
        val author = firstElementByTag(entry, "author")?.let { childText(it, "name") }
        val summary = childText(entry, "summary") ?: childText(entry, "content")

        return Article(
            id = "$sourceId:${link.ifBlank { title }}",
            source = sourceId,
            title = title,
            url = link,
            publishedAt = publishedAt,
            author = author,
            summary = summary,
            tags = emptyList()
        )
    }

    private fun parseJsonFeed(body: String, sourceId: String): List<Article> {
        val root = objectMapper.readTree(body)
        val articleNodes = when {
            root.isArray -> root
            root.has("articles") -> root.path("articles")
            root.has("items") -> root.path("items")
            else -> return emptyList()
        }

        if (!articleNodes.isArray) return emptyList()

        return articleNodes.mapNotNull { node ->
            val title = node.path("title").asText("").trim()
            val url = node.path("url").asText(node.path("link").asText(""))
            if (title.isBlank() && url.isBlank()) return@mapNotNull null

            val publishedAt = parseDate(
                node.path("publishedAt").asText(
                    node.path("pubDate").asText(
                        node.path("date").asText(null)
                    )
                )
            )
            val author = node.path("author").asText(null)
            val summary = node.path("description").asText(node.path("summary").asText(null))

            Article(
                id = "$sourceId:${url.ifBlank { title }}",
                source = sourceId,
                title = title,
                url = url,
                publishedAt = publishedAt,
                author = author,
                summary = summary,
                tags = parseTags(node)
            )
        }
    }

    private fun parseTags(node: JsonNode): List<String> {
        val tagNode = node.path("tags")
        if (!tagNode.isArray) return emptyList()
        return tagNode.map(JsonNode::asText).filter(String::isNotBlank)
    }

    private fun matchesQuery(article: Article, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isBlank()) return true
        return article.title.lowercase().contains(q) ||
            article.summary?.lowercase()?.contains(q) == true ||
            article.tags.any { tag -> tag.lowercase().contains(q) }
    }

    private fun childText(element: Element, tag: String): String? {
        val node = firstElementByTag(element, tag) ?: return null
        return node.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun firstElementByTag(parent: Element, tag: String): Element? {
        val matches = parent.getElementsByTagName(tag)
        if (matches.length == 0) return null
        return (0 until matches.length)
            .map { matches.item(it) }
            .firstOrNull { it.nodeType == Node.ELEMENT_NODE } as? Element
    }

    private fun parseDate(value: String?): Instant {
        if (value.isNullOrBlank()) return Instant.now()

        return runCatching { Instant.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .getOrElse { Instant.now() }
    }
}
