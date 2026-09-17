package com.example.mcp.mcp

import com.example.mcp.domain.Article
import com.example.mcp.domain.DriveFile
import com.example.mcp.domain.MailMessage
import com.example.mcp.domain.Quote
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class DriveSearchFilesInput(
    @field:NotBlank val query: String,
    val mimeTypes: List<String> = emptyList(),
    val modifiedAfter: Instant? = null,
    @field:Min(1) @field:Max(100) val pageSize: Int = 25,
    val pageToken: String? = null,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveSearchFilesOutput(
    val files: List<DriveFile>,
    val nextPageToken: String?,
    val source: String = "google-drive"
)

data class DriveCreateFolderInput(
    @field:NotBlank val name: String,
    val parentFolderId: String? = null,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveCreateFolderOutput(
    val folder: DriveFile,
    val source: String = "google-drive"
)

data class DriveUploadFileInput(
    @field:NotBlank val name: String,
    @field:NotBlank val contentBase64: String,
    val mimeType: String = "application/octet-stream",
    val parentFolderId: String? = null,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveUploadFileOutput(
    val file: DriveFile,
    val source: String = "google-drive"
)

data class DriveStartResumableUploadInput(
    @field:NotBlank val name: String,
    @field:Min(1) val totalSizeBytes: Long,
    val mimeType: String = "application/octet-stream",
    val parentFolderId: String? = null,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveUploadChunkInput(
    @field:NotBlank val uploadId: String,
    @field:Min(0) val offset: Long,
    @field:NotBlank val contentBase64: String,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveUploadStatusInput(
    @field:NotBlank val uploadId: String
)

data class DriveResumableUploadOutput(
    val uploadId: String,
    val uploadedBytes: Long,
    val totalSizeBytes: Long,
    val complete: Boolean,
    val file: DriveFile? = null,
    val source: String = "google-drive"
)

data class DriveStartReelUploadInput(
    @field:NotBlank val idempotencyKey: String,
    @field:NotBlank val name: String,
    @field:Min(1) val totalSizeBytes: Long,
    val mimeType: String = "video/mp4",
    val folderName: String? = null,
    val parentFolderId: String? = null,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveUploadReelChunkInput(
    @field:NotBlank val jobId: String,
    @field:Min(0) val offset: Long,
    @field:NotBlank val contentBase64: String,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveReelUploadStatusInput(@field:NotBlank val jobId: String)

data class DriveGetFileMetadataInput(
    @field:NotBlank val fileId: String,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveGetFileMetadataOutput(
    val file: DriveFile,
    val source: String = "google-drive"
)

data class DriveReadFileTextInput(
    @field:NotBlank val fileId: String,
    @field:Min(1) @field:Max(1_000_000) val maxCharacters: Int = 100_000,
    val accessToken: String? = null,
    val tenantId: String? = null,
    val userId: String? = null
)

data class DriveReadFileTextOutput(
    val fileId: String,
    val mimeType: String,
    val text: String,
    val truncated: Boolean,
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

data class GmailGetThreadInput(@field:NotBlank val threadId: String)
data class GmailGetThreadOutput(val threadId: String, val messages: List<MailMessage>)

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

data class NewsGetTopicDigestInput(
    @field:NotBlank val topic: String,
    val sources: List<String> = emptyList(),
    @field:Min(1) @field:Max(50) val limit: Int = 10,
    val language: String? = null
)

data class NewsGetTopicDigestOutput(
    val topic: String,
    val summary: String,
    val articles: List<Article>,
    val sources: List<String>,
    val generatedAt: Instant
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

data class MarketQuotesBatchInput(
    @field:Size(min = 1, max = 50) val symbols: List<@NotBlank String>,
    val providerPreference: String? = null,
    val assetClass: String? = null
)

data class MarketQuotesBatchOutput(
    val quotes: List<Quote>,
    val asOf: Instant
)
