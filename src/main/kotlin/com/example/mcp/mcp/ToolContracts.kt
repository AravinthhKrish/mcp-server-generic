package com.example.mcp.mcp

import com.example.mcp.domain.Article
import com.example.mcp.domain.DriveFile
import com.example.mcp.domain.MailMessage
import com.example.mcp.domain.Quote
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class DriveSearchFilesInput(
    @field:NotBlank val query: String,
    val mimeTypes: List<String> = emptyList(),
    val modifiedAfter: Instant? = null,
    @field:Min(1) @field:Max(100) val pageSize: Int = 25,
    val pageToken: String? = null
)

data class DriveSearchFilesOutput(
    val files: List<DriveFile>,
    val nextPageToken: String?,
    val source: String = "google-drive"
)

data class GmailSearchMessagesInput(
    @field:NotBlank val query: String,
    val labels: List<String> = emptyList(),
    @field:Min(1) @field:Max(500) val maxResults: Int = 25,
    val pageToken: String? = null
)

data class GmailSearchMessagesOutput(
    val messages: List<MailMessage>,
    val nextPageToken: String?
)

data class NewsSearchArticlesInput(
    @field:NotBlank val query: String,
    val sources: List<String> = emptyList(),
    val from: Instant? = null,
    val to: Instant? = null,
    @field:Min(1) @field:Max(100) val limit: Int = 25,
    val language: String? = null
)

data class NewsSearchArticlesOutput(
    val articles: List<Article>,
    val dedupedCount: Int,
    val freshness: String
)

data class WebSearchInput(
    @field:NotBlank val query: String,
    val sources: List<String> = emptyList(),
    @field:Min(1) @field:Max(50) val limit: Int = 10,
    val language: String? = null
)

data class WebSearchOutput(
    val results: List<Article>,
    val freshness: String
)

data class MarketQuoteInput(
    @field:NotBlank val symbol: String,
    val providerPreference: String? = null,
    val assetClass: String? = null
)

data class MarketQuoteOutput(
    val quote: List<Quote>,
    val asOf: Instant
)
