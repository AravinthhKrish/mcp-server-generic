package com.example.mcp

import com.example.mcp.domain.news.NewsProperties
import com.example.mcp.domain.news.NewsSourceConfig
import com.example.mcp.mcp.ResourceController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

class ResourceControllerTest {
    @Test
    fun `sources reflect enabled configuration without exposing credentials`() {
        val controller = ResourceController(NewsProperties(sources = listOf(
            NewsSourceConfig("enabled", "https://example.com", authToken = "secret"),
            NewsSourceConfig("disabled", "https://example.com", enabled = false)
        )), MockEnvironment())
        assertEquals(listOf(mapOf("sourceId" to "enabled", "type" to "rss")),
            controller.newsSources().data)
    }

    @Test
    fun `enabled providers do not claim unverified health`() {
        val controller = ResourceController(NewsProperties(),
            MockEnvironment().withProperty("integrations.drive.enabled", "true"))
        assertEquals("unknown", controller.providerHealth().data["google-drive"])
        assertEquals("stub", controller.providerHealth().data["gmail"])
    }
}
