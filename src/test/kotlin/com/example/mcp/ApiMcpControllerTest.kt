package com.example.mcp

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
            .jsonPath("$[0].id").exists()
            .jsonPath("$[0].name").exists()
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
}
