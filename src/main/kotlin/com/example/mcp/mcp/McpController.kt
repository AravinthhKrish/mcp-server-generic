package com.example.mcp.mcp

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/mcp/tools")
class McpController(
    private val toolService: McpToolService
) {
    @PostMapping("/drive.search_files")
    fun driveSearchFiles(@Valid @RequestBody input: DriveSearchFilesInput): DriveSearchFilesOutput {
        return toolService.driveSearchFiles(input)
    }

    @PostMapping("/gmail.search_messages")
    fun gmailSearchMessages(@Valid @RequestBody input: GmailSearchMessagesInput): GmailSearchMessagesOutput {
        return toolService.gmailSearchMessages(input)
    }

    @PostMapping("/news.search_articles")
    suspend fun newsSearchArticles(@Valid @RequestBody input: NewsSearchArticlesInput): NewsSearchArticlesOutput {
        return toolService.newsSearchArticles(input)
    }


    @PostMapping("/web_search")
    suspend fun webSearch(@Valid @RequestBody input: WebSearchInput): WebSearchOutput {
        return toolService.webSearch(input)
    }

    @PostMapping("/market.quote")
    fun marketQuote(@Valid @RequestBody input: MarketQuoteInput): MarketQuoteOutput {
        return toolService.marketQuote(input)
    }
}
