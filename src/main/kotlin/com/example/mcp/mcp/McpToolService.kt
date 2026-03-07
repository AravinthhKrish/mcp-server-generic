package com.example.mcp.mcp

import com.example.mcp.cache.CacheService
import com.example.mcp.domain.drive.GoogleDriveAdapter
import com.example.mcp.domain.gmail.GmailAdapter
import com.example.mcp.domain.market.MarketDataAdapter
import com.example.mcp.domain.news.NewsAdapter
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class McpToolService(
    private val driveAdapter: GoogleDriveAdapter,
    private val gmailAdapter: GmailAdapter,
    private val newsAdapter: NewsAdapter,
    private val marketDataAdapter: MarketDataAdapter,
    private val cacheService: CacheService
) {
    fun driveSearchFiles(input: DriveSearchFilesInput): DriveSearchFilesOutput {
        val (files, nextPageToken) = driveAdapter.searchFiles(input)
        return DriveSearchFilesOutput(files = files, nextPageToken = nextPageToken)
    }

    fun gmailSearchMessages(input: GmailSearchMessagesInput): GmailSearchMessagesOutput {
        val (messages, nextPageToken) = gmailAdapter.searchMessages(input)
        return GmailSearchMessagesOutput(messages = messages, nextPageToken = nextPageToken)
    }

    fun newsSearchArticles(input: NewsSearchArticlesInput): NewsSearchArticlesOutput {
        val articles = newsAdapter.searchArticles(input)
        return NewsSearchArticlesOutput(
            articles = articles,
            dedupedCount = 0,
            freshness = "near-real-time"
        )
    }

    fun marketQuote(input: MarketQuoteInput): MarketQuoteOutput {
        val cacheKey = "quote:${input.providerPreference ?: "default"}:${input.symbol.uppercase()}"
        val cached: MarketQuoteOutput? = cacheService.get(cacheKey)
        if (cached != null) return cached

        val output = MarketQuoteOutput(
            quote = marketDataAdapter.quote(input),
            asOf = Instant.now()
        )
        cacheService.put(cacheKey, output, Duration.ofSeconds(15))
        return output
    }
}
