package com.example.mcp.mcp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class ToolParamSpec(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

data class ToolSpec(
    val id: String,
    val name: String,
    val category: String,
    val requiresParams: Boolean,
    val description: String,
    val params: List<ToolParamSpec>
)

data class ExecuteToolRequest(
    val toolId: String? = null,
    val toolName: String? = null,
    val params: Map<String, Any?> = emptyMap()
)

data class ExecuteToolResponse(
    val success: Boolean,
    val toolId: String,
    val toolName: String,
    val result: Map<String, Any>,
    val simulated: Boolean? = null
)

@RestController
@RequestMapping("/api/mcp")
class ApiMcpController(
    private val toolService: McpToolService,
    private val objectMapper: ObjectMapper,
    private val validator: jakarta.validation.Validator,
    private val environment: org.springframework.core.env.Environment
) {
    private val tools = listOf(
        ToolSpec(
            id = "drive.search_files",
            name = "Drive File Search",
            category = "productivity",
            requiresParams = true,
            description = "Search files in Google Drive",
            params = listOf(
                ToolParamSpec("query", "string", true, "Search query"),
                ToolParamSpec("mimeTypes", "array", false, "Filter by MIME types"),
                ToolParamSpec("modifiedAfter", "string", false, "ISO timestamp lower bound"),
                ToolParamSpec("pageSize", "number", false, "Result page size (1-100)"),
                ToolParamSpec("pageToken", "string", false, "Pagination token"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "drive.create_folder",
            name = "Drive Create Folder",
            category = "productivity",
            requiresParams = true,
            description = "Create a folder in Google Drive",
            params = listOf(
                ToolParamSpec("name", "string", true, "Folder name"),
                ToolParamSpec("parentFolderId", "string", false, "Optional parent folder id"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "drive.upload_file",
            name = "Drive Upload File",
            category = "productivity",
            requiresParams = true,
            description = "Upload a file to Google Drive",
            params = listOf(
                ToolParamSpec("name", "string", true, "File name"),
                ToolParamSpec("contentBase64", "string", true, "Base64 encoded file content"),
                ToolParamSpec("mimeType", "string", false, "File MIME type"),
                ToolParamSpec("parentFolderId", "string", false, "Optional parent folder id"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "drive.file_metadata",
            name = "Drive File Metadata",
            category = "productivity",
            requiresParams = true,
            description = "Fetch metadata for a Google Drive file",
            params = listOf(
                ToolParamSpec("fileId", "string", true, "Google Drive file id"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "drive.start_resumable_upload",
            name = "Drive Start Resumable Upload",
            category = "productivity",
            requiresParams = true,
            description = "Start a bounded-memory resumable Google Drive upload",
            params = listOf(
                ToolParamSpec("name", "string", true, "File name"),
                ToolParamSpec("totalSizeBytes", "number", true, "Total file size in bytes"),
                ToolParamSpec("mimeType", "string", false, "File MIME type"),
                ToolParamSpec("parentFolderId", "string", false, "Optional parent folder id"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "drive.upload_chunk",
            name = "Drive Upload Chunk",
            category = "productivity",
            requiresParams = true,
            description = "Upload the next bounded chunk in a resumable Drive upload",
            params = listOf(
                ToolParamSpec("uploadId", "string", true, "Opaque resumable upload id"),
                ToolParamSpec("offset", "number", true, "Starting byte offset"),
                ToolParamSpec("contentBase64", "string", true, "Base64 encoded chunk"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "drive.upload_status",
            name = "Drive Upload Status",
            category = "productivity",
            requiresParams = true,
            description = "Read progress for a resumable Drive upload",
            params = listOf(
                ToolParamSpec("uploadId", "string", true, "Opaque resumable upload id")
            )
        ),
        ToolSpec(
            id = "drive.start_reel_upload",
            name = "Drive Start Reel Upload",
            category = "productivity",
            requiresParams = true,
            description = "Create an optional folder and start an idempotent resumable reel upload job",
            params = listOf(
                ToolParamSpec("idempotencyKey", "string", true, "Caller-stable idempotency key"),
                ToolParamSpec("name", "string", true, "Reel file name"),
                ToolParamSpec("totalSizeBytes", "number", true, "Total reel size in bytes"),
                ToolParamSpec("mimeType", "string", false, "Video MIME type"),
                ToolParamSpec("folderName", "string", false, "Folder to create before uploading"),
                ToolParamSpec("parentFolderId", "string", false, "Existing parent folder id"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "drive.upload_reel_chunk",
            name = "Drive Upload Reel Chunk",
            category = "productivity",
            requiresParams = true,
            description = "Upload the next chunk and persist reel job progress",
            params = listOf(
                ToolParamSpec("jobId", "string", true, "Reel upload job id"),
                ToolParamSpec("offset", "number", true, "Starting byte offset"),
                ToolParamSpec("contentBase64", "string", true, "Base64 encoded chunk"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "drive.reel_upload_status",
            name = "Drive Reel Upload Status",
            category = "productivity",
            requiresParams = true,
            description = "Read durable progress for a reel upload job",
            params = listOf(ToolParamSpec("jobId", "string", true, "Reel upload job id"))
        ),
        ToolSpec(
            id = "drive.read_file_text",
            name = "Drive Read File Text",
            category = "productivity",
            requiresParams = true,
            description = "Read bounded text content from a Drive or Google Workspace file",
            params = listOf(
                ToolParamSpec("fileId", "string", true, "Google Drive file id"),
                ToolParamSpec("maxCharacters", "number", false, "Maximum characters to return"),
                ToolParamSpec("accessToken", "string", false, "Optional Google Drive bearer token override"),
                ToolParamSpec("tenantId", "string", false, "OAuth tenant id"),
                ToolParamSpec("userId", "string", false, "OAuth user id")
            )
        ),
        ToolSpec(
            id = "gmail.search_messages",
            name = "Gmail Message Search",
            category = "productivity",
            requiresParams = true,
            description = "Search messages in Gmail",
            params = listOf(
                ToolParamSpec("query", "string", true, "Gmail query"),
                ToolParamSpec("labels", "array", false, "Label filter"),
                ToolParamSpec("maxResults", "number", false, "Max results (1-500)"),
                ToolParamSpec("pageToken", "string", false, "Pagination token")
            )
        ),
        ToolSpec(
            id = "gmail.get_thread",
            name = "Gmail Get Thread",
            category = "productivity",
            requiresParams = true,
            description = "Fetch all messages in a Gmail thread",
            params = listOf(ToolParamSpec("threadId", "string", true, "Gmail thread id"))
        ),
        ToolSpec(
            id = "news.search_articles",
            name = "News Article Search",
            category = "search",
            requiresParams = true,
            description = "Search current news articles",
            params = listOf(
                ToolParamSpec("query", "string", true, "Search query"),
                ToolParamSpec("sources", "array", false, "Source IDs"),
                ToolParamSpec("from", "string", false, "ISO timestamp lower bound"),
                ToolParamSpec("to", "string", false, "ISO timestamp upper bound"),
                ToolParamSpec("limit", "number", false, "Maximum articles"),
                ToolParamSpec("language", "string", false, "Language filter")
            )
        ),
        ToolSpec(
            id = "news.get_topic_digest",
            name = "News Topic Digest",
            category = "search",
            requiresParams = true,
            description = "Build a current digest for a topic from configured news sources",
            params = listOf(
                ToolParamSpec("topic", "string", true, "Digest topic"),
                ToolParamSpec("sources", "array", false, "Source IDs"),
                ToolParamSpec("limit", "number", false, "Maximum articles"),
                ToolParamSpec("language", "string", false, "Language filter")
            )
        ),
        ToolSpec(
            id = "web_search",
            name = "Web Search",
            category = "search",
            requiresParams = true,
            description = "Search the web for current information",
            params = listOf(
                ToolParamSpec("query", "string", true, "Search query"),
                ToolParamSpec("sources", "array", false, "Preferred source IDs"),
                ToolParamSpec("limit", "number", false, "Maximum results (1-50)"),
                ToolParamSpec("language", "string", false, "Language filter")
            )
        ),
        ToolSpec(
            id = "market.quote",
            name = "Market Quote",
            category = "finance",
            requiresParams = true,
            description = "Fetch market quote by symbol",
            params = listOf(
                ToolParamSpec("symbol", "string", true, "Ticker symbol"),
                ToolParamSpec("providerPreference", "string", false, "Provider override"),
                ToolParamSpec("assetClass", "string", false, "Asset class")
            )
        ),
        ToolSpec(
            id = "market.quotes_batch",
            name = "Market Quotes Batch",
            category = "finance",
            requiresParams = true,
            description = "Fetch normalized quotes for multiple symbols",
            params = listOf(
                ToolParamSpec("symbols", "array", true, "Ticker symbols"),
                ToolParamSpec("providerPreference", "string", false, "Provider override"),
                ToolParamSpec("assetClass", "string", false, "Asset class")
            )
        )
    )

    @GetMapping("/tools")
    fun tools(): List<ToolSpec> = tools

    @PostMapping("/execute")
    suspend fun execute(@RequestBody request: ExecuteToolRequest): ExecuteToolResponse {
        val requestedToolId = request.toolId?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "toolId is required")

        val tool = tools.firstOrNull { it.id == requestedToolId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown toolId: $requestedToolId")

        val resultPayload = when (requestedToolId) {
            "drive.search_files" -> {
                val input = validated(request.params, DriveSearchFilesInput::class.java)
                toolService.driveSearchFiles(input)
            }

            "drive.create_folder" -> {
                val input = validated(request.params, DriveCreateFolderInput::class.java)
                toolService.driveCreateFolder(input)
            }

            "drive.upload_file" -> {
                val input = validated(request.params, DriveUploadFileInput::class.java)
                toolService.driveUploadFile(input)
            }

            "drive.file_metadata" -> {
                val input = validated(request.params, DriveGetFileMetadataInput::class.java)
                toolService.driveGetFileMetadata(input)
            }

            "drive.start_resumable_upload" -> {
                val input = validated(request.params, DriveStartResumableUploadInput::class.java)
                toolService.driveStartResumableUpload(input)
            }

            "drive.upload_chunk" -> {
                val input = validated(request.params, DriveUploadChunkInput::class.java)
                toolService.driveUploadChunk(input)
            }

            "drive.upload_status" -> {
                val input = validated(request.params, DriveUploadStatusInput::class.java)
                toolService.driveUploadStatus(input)
            }

            "drive.start_reel_upload" -> {
                val input = validated(request.params, DriveStartReelUploadInput::class.java)
                toolService.driveStartReelUpload(input)
            }

            "drive.upload_reel_chunk" -> {
                val input = validated(request.params, DriveUploadReelChunkInput::class.java)
                toolService.driveUploadReelChunk(input)
            }

            "drive.reel_upload_status" -> {
                val input = validated(request.params, DriveReelUploadStatusInput::class.java)
                toolService.driveReelUploadStatus(input)
            }

            "drive.read_file_text" -> {
                val input = validated(request.params, DriveReadFileTextInput::class.java)
                toolService.driveReadFileText(input)
            }

            "gmail.search_messages" -> {
                val input = validated(request.params, GmailSearchMessagesInput::class.java)
                toolService.gmailSearchMessages(input)
            }

            "gmail.get_thread" -> {
                val input = validated(request.params, GmailGetThreadInput::class.java)
                toolService.gmailGetThread(input)
            }

            "news.search_articles" -> {
                val input = validated(request.params, NewsSearchArticlesInput::class.java)
                toolService.newsSearchArticles(input)
            }

            "news.get_topic_digest" -> {
                val input = validated(request.params, NewsGetTopicDigestInput::class.java)
                toolService.newsGetTopicDigest(input)
            }

            "web_search" -> {
                val input = validated(request.params, WebSearchInput::class.java)
                toolService.webSearch(input)
            }

            "market.quote" -> {
                val input = validated(request.params, MarketQuoteInput::class.java)
                toolService.marketQuote(input)
            }

            "market.quotes_batch" -> {
                val input = validated(request.params, MarketQuotesBatchInput::class.java)
                toolService.marketQuotesBatch(input)
            }

            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown toolId: $requestedToolId")
        }

        return ExecuteToolResponse(
            success = true,
            toolId = requestedToolId,
            toolName = request.toolName ?: tool.name,
            result = objectMapper.convertValue(resultPayload, object: TypeReference<Map<String, Any>>(){}),
            simulated = !environment.getProperty(
                "integrations.${if (requestedToolId == "web_search") "news" else requestedToolId.substringBefore('.')}.enabled",
                Boolean::class.java, false
            )
        )
    }

    private fun <T : Any> validated(params: Map<String, Any?>, type: Class<T>): T {
        val input = try {
            objectMapper.convertValue(params, type)
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tool parameters")
        }
        if (validator.validate(input).isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tool parameters")
        }
        return input
    }
}
