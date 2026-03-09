package com.example.mcp.mcp

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mcp.api.auth")
data class ApiAuthProperties(
    val token: String = "dev-token"
)
