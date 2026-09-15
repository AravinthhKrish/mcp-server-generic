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
    private val cacheService: CacheService,
    private val reelUploadPipeline: com.example.mcp.domain.drive.ReelUploadPipeline
) {
    private val logger = LoggerFactory.getLogger(McpToolService::class.java)

    fun driveSearchFiles(input: DriveSearchFilesInput): DriveSearchFilesOutput {
        logger.info("drive.search_files request received query='{}' pageSize={} mimeTypesCount={}", input.query, input.pageSize, input.mimeTypes.size)
        val (files, nextPageToken) = driveAdapter.searchFiles(input)
        logger.info("drive.search_files completed resultCount={} nextPageTokenPresent={}", files.size, nextPageToken != null)
        return DriveSearchFilesOutput(files = files, nextPageToken = nextPageToken)
    }

    fun driveCreateFolder(input: DriveCreateFolderInput): DriveCreateFolderOutput {
        logger.info("drive.create_folder request received name='{}' hasParent={}", input.name, !input.parentFolderId.isNullOrBlank())
        val folder = driveAdapter.createFolder(input)
        logger.info("drive.create_folder completed folderId='{}'", folder.id)
        return DriveCreateFolderOutput(folder = folder)
    }

    fun driveUploadFile(input: DriveUploadFileInput): DriveUploadFileOutput {
        logger.info(
            "drive.upload_file request received name='{}' mimeType='{}' hasParent={} contentLength={}",
            input.name,
            input.mimeType,
            !input.parentFolderId.isNullOrBlank(),
            input.contentBase64.length
        )
        val file = driveAdapter.uploadFile(input)
        logger.info("drive.upload_file completed fileId='{}'", file.id)
        return DriveUploadFileOutput(file = file)
    }

    fun driveGetFileMetadata(input: DriveGetFileMetadataInput): DriveGetFileMetadataOutput {
        logger.info("drive.file_metadata request received fileId='{}'", input.fileId)
        val file = driveAdapter.getFileMetadata(input)
        logger.info("drive.file_metadata completed fileId='{}' mimeType='{}'", file.id, file.mimeType)
        return DriveGetFileMetadataOutput(file = file)
    }

    fun driveStartResumableUpload(input: DriveStartResumableUploadInput): DriveResumableUploadOutput {
        logger.info("drive.start_resumable_upload name='{}' totalSizeBytes={}", input.name, input.totalSizeBytes)
        return driveAdapter.startResumableUpload(input)
    }

    fun driveUploadChunk(input: DriveUploadChunkInput): DriveResumableUploadOutput {
        logger.info("drive.upload_chunk uploadId='{}' offset={} encodedLength={}", input.uploadId, input.offset, input.contentBase64.length)
        return driveAdapter.uploadChunk(input)
    }

    fun driveUploadStatus(input: DriveUploadStatusInput): DriveResumableUploadOutput {
        return driveAdapter.uploadStatus(input.uploadId)
    }

    fun driveStartReelUpload(input: DriveStartReelUploadInput): com.example.mcp.domain.drive.ReelUploadJob =
        reelUploadPipeline.start(input)

    fun driveUploadReelChunk(input: DriveUploadReelChunkInput): com.example.mcp.domain.drive.ReelUploadJob =
        reelUploadPipeline.uploadChunk(input)

    fun driveReelUploadStatus(input: DriveReelUploadStatusInput): com.example.mcp.domain.drive.ReelUploadJob =
        reelUploadPipeline.status(input.jobId)

    fun driveReadFileText(input: DriveReadFileTextInput): DriveReadFileTextOutput =
        driveAdapter.readFileText(input)

    fun gmailSearchMessages(input: GmailSearchMessagesInput): GmailSearchMessagesOutput {
        logger.info("gmail.search_messages request received query='{}' maxResults={} labelsCount={}", input.query, input.maxResults, input.labels.size)
        val (messages, nextPageToken) = gmailAdapter.searchMessages(input)
        logger.info("gmail.search_messages completed resultCount={} nextPageTokenPresent={}", messages.size, nextPageToken != null)
        return GmailSearchMessagesOutput(messages = messages, nextPageToken = nextPageToken)
    }

    fun gmailGetThread(input: GmailGetThreadInput): GmailGetThreadOutput =
        GmailGetThreadOutput(input.threadId, gmailAdapter.getThread(input))

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

    suspend fun newsGetTopicDigest(input: NewsGetTopicDigestInput): NewsGetTopicDigestOutput {
        val articles = newsAdapter.searchArticles(NewsSearchArticlesInput(
            query = input.topic, sources = input.sources, limit = input.limit, language = input.language
        ))
        val summary = if (articles.isEmpty()) {
            "No matching articles were found."
        } else {
            articles.take(5).joinToString(" ") { article -> "${article.title}." }
        }
        return NewsGetTopicDigestOutput(
            topic = input.topic,
            summary = summary,
            articles = articles,
            sources = articles.map { it.source }.distinct(),
            generatedAt = Instant.now()
        )
    }


    suspend fun webSearch(input: WebSearchInput): WebSearchOutput {
        logger.info("web_search request received query='{}' limit={} sourcesCount={}", input.query, input.limit, input.sources.size)
        val articles = newsAdapter.searchArticles(
            NewsSearchArticlesInput(
                query = input.query,
                sources = input.sources,
                limit = input.limit,
                language = input.language
            )
        )
        logger.info("web_search completed resultCount={}", articles.size)
        return WebSearchOutput(results = articles, freshness = "near-real-time")
    }
    suspend fun marketQuote(input: MarketQuoteInput): MarketQuoteOutput {
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
        logger.info("market.quote completed symbol='{}' provider='{}'", output.quote.firstOrNull()?.symbol, output.quote.firstOrNull()?.provider)
        return output
    }

    suspend fun marketQuotesBatch(input: MarketQuotesBatchInput): MarketQuotesBatchOutput {
        val quotes = input.symbols.flatMap { symbol ->
            marketQuote(MarketQuoteInput(symbol, input.providerPreference, input.assetClass)).quote
        }
        return MarketQuotesBatchOutput(quotes, Instant.now())
    }
}
