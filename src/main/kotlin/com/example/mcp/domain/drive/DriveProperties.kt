package com.example.mcp.domain.drive

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "integrations.drive")
data class DriveProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://www.googleapis.com/drive/v3",
    val uploadBaseUrl: String = "https://www.googleapis.com/upload/drive/v3",
    val accessToken: String = "",
    val connectTimeoutMs: Long = 8000,
    val readTimeoutMs: Long = 60000,
    val maxChunkBytes: Int = 8 * 1024 * 1024,
    val maxRetries: Int = 2,
    val retryBackoffMs: Long = 200
)
