package com.example.mcp.domain.news

import com.example.mcp.mcp.NewsSearchArticlesInput
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress

class ApiNewsAdapterTest {
    @Test
    fun `aggregates from multiple sources in one search`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/rss") { exchange ->
            val body = """
                <rss><channel>
                  <item>
                    <title>Kotlin markets update</title>
                    <link>https://news.local/rss/1</link>
                    <description>Market brief from rss</description>
                  </item>
                </channel></rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }

        server.createContext("/json") { exchange ->
            val body = """
                {"articles":[{"title":"Kotlin API headline","url":"https://news.local/json/1","summary":"Market brief from json"}]}
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val baseUrl = "http://localhost:${server.address.port}"
            val properties = NewsProperties(
                enabled = true,
                sources = listOf(
                    NewsSourceConfig(id = "rss-source", url = "$baseUrl/rss", type = SourceType.RSS),
                    NewsSourceConfig(id = "json-source", url = "$baseUrl/json", type = SourceType.JSON)
                )
            )

            val webClient = NewsWebClientConfiguration().newsWebClient(WebClient.builder(), properties)
            val adapter = ApiNewsAdapter(properties, ObjectMapper(), webClient)
            val results = runBlocking { adapter.searchArticles(NewsSearchArticlesInput(query = "kotlin", limit = 10)) }

            assertEquals(2, results.size)
            assertTrue(results.any { it.source == "rss-source" })
            assertTrue(results.any { it.source == "json-source" })
        } finally {
            server.stop(0)
        }
    }
}
