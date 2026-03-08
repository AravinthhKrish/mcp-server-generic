package com.example.mcp.domain.news

import com.example.mcp.domain.Article
import com.example.mcp.mcp.NewsSearchArticlesInput
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
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
    private val objectMapper: ObjectMapper
) : NewsAdapter {
    private val restClient = RestClient.builder().build()

    override fun searchArticles(input: NewsSearchArticlesInput): List<Article> {
        val selectedSources = properties.sources
            .asSequence()
            .filter { it.enabled }
            .filter { input.sources.isEmpty() || input.sources.contains(it.id) }
            .toList()

        val aggregated = selectedSources.flatMap { source ->
            runCatching { fetchFromSource(source, input) }.getOrDefault(emptyList())
        }

        return aggregated
            .filter { matchesQuery(it, input.query) }
            .filter { input.from == null || !it.publishedAt.isBefore(input.from) }
            .filter { input.to == null || !it.publishedAt.isAfter(input.to) }
            .sortedByDescending(Article::publishedAt)
            .take(input.limit)
    }

    private fun fetchFromSource(source: NewsSourceConfig, input: NewsSearchArticlesInput): List<Article> {
        val responseBody = restClient.get()
            .uri(buildUri(source, input.query))
            .headers { headers -> addAuthHeaders(headers, source) }
            .retrieve()
            .body(String::class.java)
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
        return URI.create("${source.url}$separator$queryParam=$query")
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
        val items = when {
            root.isArray -> root
            root.path("articles").isArray -> root.path("articles")
            root.path("items").isArray -> root.path("items")
            else -> objectMapper.createArrayNode()
        }

        return items.map { node ->
            val title = node.path("title").asText("")
            val url = node.path("url").asText(node.path("link").asText(""))
            val publishedRaw = node.path("publishedAt").asText(node.path("published_at").asText(""))
            val publishedAt = parseDate(publishedRaw)
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
