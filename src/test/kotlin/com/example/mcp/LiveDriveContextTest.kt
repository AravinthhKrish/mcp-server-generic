package com.example.mcp

import com.example.mcp.domain.drive.ApiGoogleDriveAdapter
import com.example.mcp.domain.drive.GoogleDriveAdapter
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = [
    "integrations.drive.enabled=true",
    "integrations.drive.access-token=context-test-token",
    "integrations.news.enabled=false",
    "integrations.market.enabled=false"
])
class LiveDriveContextTest(@Autowired private val adapter: GoogleDriveAdapter) {
    @Test
    fun `live Drive configuration wires OAuth and upload session dependencies`() {
        assertInstanceOf(ApiGoogleDriveAdapter::class.java, adapter)
    }
}
