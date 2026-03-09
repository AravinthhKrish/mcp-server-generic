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
    private val objectMapper: ObjectMapper
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
                ToolParamSpec("pageToken", "string", false, "Pagination token")
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
                val input = objectMapper.convertValue(request.params, DriveSearchFilesInput::class.java)
                toolService.driveSearchFiles(input)
            }

            "gmail.search_messages" -> {
                val input = objectMapper.convertValue(request.params, GmailSearchMessagesInput::class.java)
                toolService.gmailSearchMessages(input)
            }

            "news.search_articles" -> {
                val input = objectMapper.convertValue(request.params, NewsSearchArticlesInput::class.java)
                toolService.newsSearchArticles(input)
            }

            "web_search" -> {
                val input = objectMapper.convertValue(request.params, WebSearchInput::class.java)
                toolService.webSearch(input)
            }

            "market.quote" -> {
                val input = objectMapper.convertValue(request.params, MarketQuoteInput::class.java)
                toolService.marketQuote(input)
            }

            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown toolId: $requestedToolId")
        }

        return ExecuteToolResponse(
            success = true,
            toolId = requestedToolId,
            toolName = request.toolName ?: tool.name,
            result = objectMapper.convertValue(resultPayload, object: TypeReference<Map<String, Any>>(){}),
            simulated = true
        )
    }
}
