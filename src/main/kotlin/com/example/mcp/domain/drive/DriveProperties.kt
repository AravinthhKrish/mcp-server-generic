package com.example.mcp.domain.drive

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "integrations.drive")
data class DriveProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://www.googleapis.com/drive/v3",
    val uploadBaseUrl: String = "https://www.googleapis.com/upload/drive/v3",
    val accessToken: String = ""
)
