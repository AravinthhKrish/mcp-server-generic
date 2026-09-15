package com.example.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.EntityExchangeResult

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["integrations.news.enabled=false", "integrations.market.enabled=false"]
)
@AutoConfigureWebTestClient
class McpProtocolTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val objectMapper: ObjectMapper
) {
    private val headers: (org.springframework.http.HttpHeaders) -> Unit = {
        it.setBearerAuth("dev-token")
        it.contentType = MediaType.APPLICATION_JSON
        it.accept = listOf(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
    }

    @Test
    fun `streamable MCP lifecycle lists and calls tools`() {
        val initialize = post(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"evaluation","version":"1.0"}}}"""
        )
        val sessionId = initialize.responseHeaders.getFirst("Mcp-Session-Id")
        assertNotNull(sessionId)
        val initializeJson = parseResponse(initialize.responseBody!!)
        assertNotNull(initializeJson.path("result").path("serverInfo").path("name").asText(null))

        client.post().uri("/mcp").headers(headers)
            .header("Mcp-Session-Id", sessionId)
            .header("Mcp-Protocol-Version", "2025-06-18")
            .bodyValue("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
            .exchange().expectStatus().is2xxSuccessful

        val listed = post(
            """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""",
            sessionId
        )
        val tools = parseResponse(listed.responseBody!!).path("result").path("tools")
        org.junit.jupiter.api.Assertions.assertTrue(tools.any { it.path("name").asText() == "drive.create_folder" })
        val driveTool = tools.first { it.path("name").asText() == "drive.create_folder" }
        org.junit.jupiter.api.Assertions.assertTrue(driveTool.path("inputSchema").path("required").any { it.asText() == "name" })

        val called = post(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"drive.create_folder","arguments":{"name":"Protocol Reels"}}}""",
            sessionId
        )
        val result = parseResponse(called.responseBody!!).path("result")
        org.junit.jupiter.api.Assertions.assertFalse(result.path("isError").asBoolean(true))
        org.junit.jupiter.api.Assertions.assertEquals("drive.create_folder", result.path("structuredContent").path("toolId").asText())
    }

    @Test
    fun `MCP endpoint requires authentication`() {
        client.post().uri("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
            .exchange().expectStatus().isUnauthorized
    }

    private fun post(body: String, sessionId: String? = null): EntityExchangeResult<ByteArray> {
        var request = client.post().uri("/mcp").headers(headers)
            .header("Mcp-Protocol-Version", "2025-06-18")
        if (sessionId != null) request = request.header("Mcp-Session-Id", sessionId)
        return request.bodyValue(body).exchange().expectStatus().isOk.expectBody().returnResult()
    }

    private fun parseResponse(body: ByteArray): com.fasterxml.jackson.databind.JsonNode {
        val text = body.toString(Charsets.UTF_8).trim()
        if (text.startsWith("{")) return objectMapper.readTree(text)
        val data = text.lineSequence()
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trim() }
        return objectMapper.readTree(data)
    }
}
