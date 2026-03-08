package com.example.mcp.domain.gmail

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "integrations.gmail")
data class GmailProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://gmail.googleapis.com/gmail/v1",
    val userId: String = "me",
    val accessToken: String = ""
)
