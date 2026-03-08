package com.example.mcp.domain.news

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "integrations.news")
data class NewsProperties(
    val enabled: Boolean = false,
    val connectTimeoutMs: Long = 5000,
    val readTimeoutMs: Long = 8000,
    val sources: List<NewsSourceConfig> = emptyList()
)

data class NewsSourceConfig(
    val id: String,
    val url: String,
    val type: SourceType = SourceType.RSS,
    val enabled: Boolean = true,
    val auth: SourceAuth = SourceAuth.NONE,
    val authHeader: String = "Authorization",
    val authToken: String = "",
    val queryParam: String? = null
)

enum class SourceType {
    RSS,
    ATOM,
    JSON
}

enum class SourceAuth {
    NONE,
    BEARER,
    HEADER
}
