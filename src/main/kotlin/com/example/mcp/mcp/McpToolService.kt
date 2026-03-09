package com.example.mcp.mcp

import com.example.mcp.cache.CacheService
import com.example.mcp.domain.drive.GoogleDriveAdapter
import com.example.mcp.domain.gmail.GmailAdapter
import com.example.mcp.domain.market.MarketDataAdapter
import com.example.mcp.domain.news.NewsAdapter
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(McpToolService::class.java)

    fun driveSearchFiles(input: DriveSearchFilesInput): DriveSearchFilesOutput {
        logger.info("drive.search_files request received query='{}' pageSize={} mimeTypesCount={}", input.query, input.pageSize, input.mimeTypes.size)
        val (files, nextPageToken) = driveAdapter.searchFiles(input)
        logger.info("drive.search_files completed resultCount={} nextPageTokenPresent={}", files.size, nextPageToken != null)
        return DriveSearchFilesOutput(files = files, nextPageToken = nextPageToken)
    }

    fun gmailSearchMessages(input: GmailSearchMessagesInput): GmailSearchMessagesOutput {
        logger.info("gmail.search_messages request received query='{}' maxResults={} labelsCount={}", input.query, input.maxResults, input.labels.size)
        val (messages, nextPageToken) = gmailAdapter.searchMessages(input)
        logger.info("gmail.search_messages completed resultCount={} nextPageTokenPresent={}", messages.size, nextPageToken != null)
        return GmailSearchMessagesOutput(messages = messages, nextPageToken = nextPageToken)
    }

    suspend fun newsSearchArticles(input: NewsSearchArticlesInput): NewsSearchArticlesOutput {
        logger.info("news.search_articles request received query='{}' limit={} sourcesCount={}", input.query, input.limit, input.sources.size)
        val articles = newsAdapter.searchArticles(input)
        logger.info("news.search_articles completed articleCount={} freshness={}", articles.size, "near-real-time")
        return NewsSearchArticlesOutput(
            articles = articles,
            dedupedCount = 0,
            freshness = "near-real-time"
        )
    }

    fun marketQuote(input: MarketQuoteInput): MarketQuoteOutput {
        val normalizedSymbol = input.symbol.uppercase()
        val cacheKey = "quote:${input.providerPreference ?: "default"}:$normalizedSymbol"
        val cached: MarketQuoteOutput? = cacheService.get(cacheKey)
        if (cached != null) {
            logger.info("market.quote cache hit symbol='{}' providerPreference='{}'", normalizedSymbol, input.providerPreference ?: "default")
            return cached
        }

        logger.info("market.quote cache miss symbol='{}' providerPreference='{}'", normalizedSymbol, input.providerPreference ?: "default")
        val output = MarketQuoteOutput(
            quote = marketDataAdapter.quote(input),
            asOf = Instant.now()
        )
        cacheService.put(cacheKey, output, Duration.ofSeconds(15))
        logger.info("market.quote completed symbol='{}' provider='{}'", output.quote.symbol, output.quote.provider)
        return output
    }
}
