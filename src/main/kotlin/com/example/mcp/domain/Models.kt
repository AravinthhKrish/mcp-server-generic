package com.example.mcp.domain

import java.time.Instant

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val owners: List<String>,
    val modifiedTime: Instant,
    val webViewLink: String?
)

data class MailMessage(
    val id: String,
    val threadId: String,
    val from: String,
    val to: List<String>,
    val subject: String,
    val snippet: String,
    val labels: List<String>,
    val internalDate: Instant
)

data class Article(
    val id: String,
    val source: String,
    val title: String,
    val url: String,
    val publishedAt: Instant,
    val author: String?,
    val summary: String?,
    val tags: List<String>
)

data class Quote(
    val symbol: String,
    val venue: String,
    val currency: String,
    val last: Double,
    val bid: Double?,
    val ask: Double?,
    val change: Double,
    val changePercent: Double,
    val timestamp: Instant,
    val provider: String
)
