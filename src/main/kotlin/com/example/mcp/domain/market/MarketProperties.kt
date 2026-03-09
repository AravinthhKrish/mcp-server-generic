package com.example.mcp.domain.market

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "integrations.market")
data class MarketProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val quotePath: String = "/mcp/tools/market.quote",
    val authToken: String = ""
)
