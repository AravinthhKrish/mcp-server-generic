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

    @PostMapping("/drive.create_folder")
    fun driveCreateFolder(@Valid @RequestBody input: DriveCreateFolderInput): DriveCreateFolderOutput {
        return toolService.driveCreateFolder(input)
    }

    @PostMapping("/drive.upload_file")
    fun driveUploadFile(@Valid @RequestBody input: DriveUploadFileInput): DriveUploadFileOutput {
        return toolService.driveUploadFile(input)
    }

    @PostMapping("/drive.file_metadata")
    fun driveFileMetadata(@Valid @RequestBody input: DriveGetFileMetadataInput): DriveGetFileMetadataOutput {
        return toolService.driveGetFileMetadata(input)
    }

    @PostMapping("/drive.start_resumable_upload")
    fun driveStartResumableUpload(@Valid @RequestBody input: DriveStartResumableUploadInput): DriveResumableUploadOutput {
        return toolService.driveStartResumableUpload(input)
    }

    @PostMapping("/drive.upload_chunk")
    fun driveUploadChunk(@Valid @RequestBody input: DriveUploadChunkInput): DriveResumableUploadOutput {
        return toolService.driveUploadChunk(input)
    }

    @PostMapping("/drive.upload_status")
    fun driveUploadStatus(@Valid @RequestBody input: DriveUploadStatusInput): DriveResumableUploadOutput {
        return toolService.driveUploadStatus(input)
    }

    @PostMapping("/drive.start_reel_upload")
    fun driveStartReelUpload(@Valid @RequestBody input: DriveStartReelUploadInput): com.example.mcp.domain.drive.ReelUploadJob =
        toolService.driveStartReelUpload(input)

    @PostMapping("/drive.upload_reel_chunk")
    fun driveUploadReelChunk(@Valid @RequestBody input: DriveUploadReelChunkInput): com.example.mcp.domain.drive.ReelUploadJob =
        toolService.driveUploadReelChunk(input)

    @PostMapping("/drive.reel_upload_status")
    fun driveReelUploadStatus(@Valid @RequestBody input: DriveReelUploadStatusInput): com.example.mcp.domain.drive.ReelUploadJob =
        toolService.driveReelUploadStatus(input)

    @PostMapping("/drive.read_file_text")
    fun driveReadFileText(@Valid @RequestBody input: DriveReadFileTextInput): DriveReadFileTextOutput =
        toolService.driveReadFileText(input)

    @PostMapping("/gmail.search_messages")
    fun gmailSearchMessages(@Valid @RequestBody input: GmailSearchMessagesInput): GmailSearchMessagesOutput {
        return toolService.gmailSearchMessages(input)
    }

    @PostMapping("/gmail.get_thread")
    fun gmailGetThread(@Valid @RequestBody input: GmailGetThreadInput): GmailGetThreadOutput =
        toolService.gmailGetThread(input)

    @PostMapping("/news.search_articles")
    suspend fun newsSearchArticles(@Valid @RequestBody input: NewsSearchArticlesInput): NewsSearchArticlesOutput {
        return toolService.newsSearchArticles(input)
    }

    @PostMapping("/news.get_topic_digest")
    suspend fun newsGetTopicDigest(@Valid @RequestBody input: NewsGetTopicDigestInput): NewsGetTopicDigestOutput =
        toolService.newsGetTopicDigest(input)


    @PostMapping("/web_search")
    suspend fun webSearch(@Valid @RequestBody input: WebSearchInput): WebSearchOutput {
        return toolService.webSearch(input)
    }

    @PostMapping("/market.quote")
    suspend fun marketQuote(@Valid @RequestBody input: MarketQuoteInput): MarketQuoteOutput {
        return toolService.marketQuote(input)
    }

    @PostMapping("/market.quotes_batch")
    suspend fun marketQuotesBatch(@Valid @RequestBody input: MarketQuotesBatchInput): MarketQuotesBatchOutput =
        toolService.marketQuotesBatch(input)
}
