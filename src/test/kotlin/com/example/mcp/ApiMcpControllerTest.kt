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
class ApiMcpControllerTest(
    @Autowired private val webTestClient: WebTestClient
) {
    @Test
    fun `tools endpoint requires auth`() {
        webTestClient.get()
            .uri("/api/mcp/tools")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `tools endpoint returns tool catalog with auth`() {
        webTestClient.get()
            .uri("/api/mcp/tools")
            .header("Authorization", "Bearer dev-token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[?(@.id == 'drive.create_folder')]").exists()
            .jsonPath("$[?(@.id == 'drive.upload_file')]").exists()
            .jsonPath("$[?(@.id == 'drive.file_metadata')]").exists()
    }

    @Test
    fun `execute endpoint validates toolId`() {
        webTestClient.post()
            .uri("/api/mcp/execute")
            .header("Authorization", "Bearer dev-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"params":{"query":"foo"}}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `execute endpoint returns market quote response shape`() {
        webTestClient.post()
            .uri("/api/mcp/execute")
            .header("Authorization", "Bearer dev-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"toolId":"market.quote","toolName":"Market Quote","params":{"symbol":"AAPL"}}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.toolId").isEqualTo("market.quote")
            .jsonPath("$.toolName").isEqualTo("Market Quote")
            .jsonPath("$.result").isNotEmpty
            .jsonPath("$.simulated").isEqualTo(true)
    }

    @Test
    fun `execute endpoint supports drive upload tool`() {
        webTestClient.post()
            .uri("/api/mcp/execute")
            .header("Authorization", "Bearer dev-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"toolId":"drive.upload_file","toolName":"Drive Upload File","params":{"name":"reel.mp4","mimeType":"video/mp4","contentBase64":"aGVsbG8=","parentFolderId":"folder_001"}}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.toolId").isEqualTo("drive.upload_file")
            .jsonPath("$.toolName").isEqualTo("Drive Upload File")
            .jsonPath("$.result.file.id").isEqualTo("file_upload_001")
            .jsonPath("$.result.file.sizeBytes").isEqualTo(5)
    }

    @Test
    fun `execute endpoint supports drive metadata tool`() {
        webTestClient.post()
            .uri("/api/mcp/execute")
            .header("Authorization", "Bearer dev-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"toolId":"drive.file_metadata","toolName":"Drive File Metadata","params":{"fileId":"file_upload_001"}}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.toolId").isEqualTo("drive.file_metadata")
            .jsonPath("$.result.file.mimeType").isEqualTo("video/mp4")
    }


    @Test
    fun `execute endpoint supports web search tool`() {
        webTestClient.post()
            .uri("/api/mcp/execute")
            .header("Authorization", "Bearer dev-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"toolId":"web_search","toolName":"Web Search","params":{"query":"kotlin","limit":2}}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.toolId").isEqualTo("web_search")
            .jsonPath("$.toolName").isEqualTo("Web Search")
            .jsonPath("$.result").isNotEmpty
    }

}
