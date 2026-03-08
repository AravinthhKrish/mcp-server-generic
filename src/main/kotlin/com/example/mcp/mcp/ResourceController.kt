package com.example.mcp.mcp

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class McpResource<T>(
    val uri: String,
    val data: T
)

@RestController
@RequestMapping("/mcp/resources")
class ResourceController {
    @GetMapping("/news/sources")
    fun newsSources(): McpResource<List<Map<String, String>>> = McpResource(
        uri = "resource://news/sources",
        data = listOf(
            mapOf("sourceId" to "reuters", "type" to "rss"),
            mapOf("sourceId" to "alpha-vantage-news", "type" to "api")
        )
    )

    @GetMapping("/system/provider-health")
    fun providerHealth(): McpResource<Map<String, String>> = McpResource(
        uri = "resource://system/provider-health",
        data = mapOf(
            "google-drive" to "unknown",
            "gmail" to "unknown",
            "news" to "healthy",
            "market" to "healthy"
        )
    )
}
