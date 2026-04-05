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
    @Autowired private val webTestClient: WebTestClient
) {
    @Test
    fun `drive create folder returns normalized payload`() {
        webTestClient.post()
            .uri("/mcp/tools/drive.create_folder")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Reels","parentFolderId":"root"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.folder.id").isEqualTo("folder_001")
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
            .jsonPath("$.file.id").isEqualTo("file_upload_001")
            .jsonPath("$.file.mimeType").isEqualTo("video/mp4")
            .jsonPath("$.file.parents[0]").isEqualTo("folder_001")
            .jsonPath("$.file.sizeBytes").isEqualTo(5)
    }

    @Test
    fun `drive file metadata returns normalized payload`() {
        webTestClient.post()
            .uri("/mcp/tools/drive.file_metadata")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"fileId":"file_upload_001"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.file.id").isEqualTo("file_upload_001")
            .jsonPath("$.file.mimeType").isEqualTo("video/mp4")
            .jsonPath("$.file.sizeBytes").isEqualTo(1024)
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
