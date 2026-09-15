package com.example.mcp

import com.example.mcp.mcp.ToolErrorHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.http.HttpStatus

class ToolErrorHandlerTest {
    @Test
    fun `provider bodies never appear in client errors`() {
        val handler = ToolErrorHandler()
        for (status in listOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
            HttpStatus.NOT_FOUND, HttpStatus.TOO_MANY_REQUESTS)) {
            val error = HttpClientErrorException.create(status, "secret upstream text",
                HttpHeaders(), "sensitive provider response".toByteArray(), null)
            val result = handler.providerFailure(error)
            assertEquals(if (status == HttpStatus.UNAUTHORIZED) HttpStatus.FORBIDDEN else status,
                result.statusCode)
            assertEquals("Provider request failed", result.body!!.message)
        }
    }
}
