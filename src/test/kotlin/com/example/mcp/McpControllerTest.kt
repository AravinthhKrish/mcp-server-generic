package com.example.mcp

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class McpControllerTest(
    @Autowired private val webTestClient: WebTestClient
) {
    @Test
    fun `market quote returns normalized payload`() {
        webTestClient.post()
            .uri("/mcp/tools/market.quote")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"symbol":"AAPL"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.quote.symbol").isEqualTo("AAPL")
            .jsonPath("$.quote.provider").exists()
    }
}
