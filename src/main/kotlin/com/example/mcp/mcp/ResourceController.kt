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
class ResourceController(
    private val news: com.example.mcp.domain.news.NewsProperties,
    private val environment: org.springframework.core.env.Environment
) {
    @GetMapping("/news/sources")
    fun newsSources(): McpResource<List<Map<String, String>>> = McpResource(
        uri = "resource://news/sources",
        data = news.sources.filter { it.enabled }.map {
            mapOf("sourceId" to it.id, "type" to it.type.name.lowercase())
        }
    )

    @GetMapping("/system/provider-health")
    fun providerHealth(): McpResource<Map<String, String>> = McpResource(
        uri = "resource://system/provider-health",
        data = mapOf(
            "google-drive" to status("drive"),
            "gmail" to status("gmail"),
            "news" to status("news"),
            "market" to status("market")
        )
    )

    private fun status(provider: String): String =
        if (environment.getProperty("integrations.$provider.enabled", Boolean::class.java, false))
            "unknown" else "stub"
}
