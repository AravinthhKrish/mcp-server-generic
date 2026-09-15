package com.example.mcp

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "integrations.news.enabled=false",
        "integrations.market.enabled=false"
    ]
)
@AutoConfigureWebTestClient
class McpControllerTest(
    @Autowired client: WebTestClient
) {
    @Test
    fun `phase two tools return bounded normalized payloads`() {
        val uploaded = webTestClient.post().uri("/mcp/tools/drive.upload_file")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"notes.txt","mimeType":"text/plain","contentBase64":"aGVsbG8="}""")
            .exchange().expectStatus().isOk
            .expectBody(com.example.mcp.mcp.DriveUploadFileOutput::class.java)
            .returnResult().responseBody!!

        webTestClient.post().uri("/mcp/tools/drive.read_file_text")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("fileId" to uploaded.file.id, "maxCharacters" to 2))
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.text").isEqualTo("he")
            .jsonPath("$.truncated").isEqualTo(true)

        webTestClient.post().uri("/mcp/tools/gmail.get_thread")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("threadId" to "thread-1"))
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.messages[0].threadId").isEqualTo("thread-1")

        webTestClient.post().uri("/mcp/tools/market.quotes_batch")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("symbols" to listOf("AAPL", "MSFT")))
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.quotes.length()").isEqualTo(2)

        webTestClient.post().uri("/mcp/tools/news.get_topic_digest")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("topic" to "markets", "limit" to 2))
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.topic").isEqualTo("markets")
            .jsonPath("$.summary").isNotEmpty
    }

    @Test
    fun `reel pipeline is idempotent and exposes recoverable job status`() {
        val request = """{"idempotencyKey":"reel-order-42","name":"campaign.mp4","totalSizeBytes":5,"folderName":"Campaign"}"""
        val first = webTestClient.post().uri("/mcp/tools/drive.start_reel_upload")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
            .exchange().expectStatus().isOk
            .expectBody(com.example.mcp.domain.drive.ReelUploadJob::class.java)
            .returnResult().responseBody!!
        val repeated = webTestClient.post().uri("/mcp/tools/drive.start_reel_upload")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
            .exchange().expectStatus().isOk
            .expectBody(com.example.mcp.domain.drive.ReelUploadJob::class.java)
            .returnResult().responseBody!!
        org.junit.jupiter.api.Assertions.assertEquals(first.jobId, repeated.jobId)

        webTestClient.post().uri("/mcp/tools/drive.upload_reel_chunk")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("jobId" to first.jobId, "offset" to 0, "contentBase64" to "aGVsbG8="))
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.state").isEqualTo("COMPLETE")
            .jsonPath("$.uploadedBytes").isEqualTo(5)

        webTestClient.post().uri("/mcp/tools/drive.reel_upload_status")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("jobId" to first.jobId))
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.file.name").isEqualTo("campaign.mp4")
    }

    @Test
    fun `resumable upload reports progress and preserves completed metadata`() {
        val started = webTestClient.post().uri("/mcp/tools/drive.start_resumable_upload")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"large-reel.mp4","mimeType":"video/mp4","totalSizeBytes":5}""")
            .exchange().expectStatus().isOk
            .expectBody(com.example.mcp.mcp.DriveResumableUploadOutput::class.java)
            .returnResult().responseBody!!
        org.junit.jupiter.api.Assertions.assertEquals(0, started.uploadedBytes)

        val completed = webTestClient.post().uri("/mcp/tools/drive.upload_chunk")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("uploadId" to started.uploadId, "offset" to 0, "contentBase64" to "aGVsbG8="))
            .exchange().expectStatus().isOk
            .expectBody(com.example.mcp.mcp.DriveResumableUploadOutput::class.java)
            .returnResult().responseBody!!
        org.junit.jupiter.api.Assertions.assertTrue(completed.complete)
        org.junit.jupiter.api.Assertions.assertEquals(5, completed.uploadedBytes)

        webTestClient.post().uri("/mcp/tools/drive.upload_status")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("uploadId" to started.uploadId))
            .exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.file.name").isEqualTo("large-reel.mp4")
            .jsonPath("$.file.sizeBytes").isEqualTo(5)
    }

    private val webTestClient = client.mutate()
        .defaultHeader("Authorization", "Bearer dev-token").build()

    @Test
    fun `direct tools and resources reject unauthenticated requests`() {
        val anonymous = webTestClient.mutate().defaultHeaders { it.clear() }.build()
        anonymous.post().uri("/mcp/tools/drive.create_folder")
            .bodyValue(mapOf("name" to "Unauthorized"))
            .exchange().expectStatus().isUnauthorized
        anonymous.get().uri("/mcp/resources/system/provider-health")
            .exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `drive create folder returns normalized payload`() {
        webTestClient.post()
            .uri("/mcp/tools/drive.create_folder")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Reels","parentFolderId":"root"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.folder.id").isNotEmpty
            .jsonPath("$.folder.mimeType").isEqualTo("application/vnd.google-apps.folder")
            .jsonPath("$.folder.parents[0]").isEqualTo("root")
    }

    @Test
    fun `drive upload file returns normalized payload`() {
        webTestClient.post()
            .uri("/mcp/tools/drive.upload_file")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"reel.mp4","mimeType":"video/mp4","contentBase64":"aGVsbG8=","parentFolderId":"folder_001"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.file.id").isNotEmpty
            .jsonPath("$.file.mimeType").isEqualTo("video/mp4")
            .jsonPath("$.file.parents[0]").isEqualTo("folder_001")
            .jsonPath("$.file.sizeBytes").isEqualTo(5)
    }

    @Test
    fun `drive file metadata returns normalized payload`() {
        val uploaded = webTestClient.post().uri("/mcp/tools/drive.upload_file")
            .header("Authorization", "Bearer dev-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"reel.mp4","mimeType":"video/mp4","contentBase64":"aGVsbG8="}""")
            .exchange().expectStatus().isOk
            .expectBody(com.example.mcp.mcp.DriveUploadFileOutput::class.java)
            .returnResult().responseBody!!
        webTestClient.post()
            .uri("/mcp/tools/drive.file_metadata")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("fileId" to uploaded.file.id))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.file.id").isEqualTo(uploaded.file.id)
            .jsonPath("$.file.mimeType").isEqualTo("video/mp4")
            .jsonPath("$.file.sizeBytes").isEqualTo(5)
    }

    @Test
    fun `market quote returns normalized payload`() {
        webTestClient.post()
            .uri("/mcp/tools/market.quote")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"symbol":"AAPL"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.quote[0].symbol").isEqualTo("AAPL")
            .jsonPath("$.quote[0].provider").exists()
    }


    @Test
    fun `web search returns payload`() {
        webTestClient.post()
            .uri("/mcp/tools/web_search")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"query":"kotlin","limit":2}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.results").isArray
            .jsonPath("$.freshness").isEqualTo("near-real-time")
    }

}
