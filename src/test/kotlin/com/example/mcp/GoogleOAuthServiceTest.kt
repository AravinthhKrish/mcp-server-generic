package com.example.mcp

import com.example.mcp.auth.GoogleAuthorizationRequest
import com.example.mcp.auth.GoogleOAuthProperties
import com.example.mcp.auth.GoogleOAuthService
import com.example.mcp.auth.InMemorySecretStore
import com.example.mcp.auth.InMemoryTokenStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class GoogleOAuthServiceTest {
    @Test
    fun `consent is one-time tenant isolated and expired access tokens refresh`() {
        val calls = AtomicInteger()
        val forms = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/token") { exchange ->
            forms += exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val body = if (calls.incrementAndGet() == 1) {
                """{"access_token":"access-1","refresh_token":"refresh-1","expires_in":1}"""
            } else {
                """{"access_token":"access-2","expires_in":3600}"""
            }
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        try {
            val service = GoogleOAuthService(
                GoogleOAuthProperties(
                    enabled = true,
                    clientId = "client",
                    clientSecret = "secret",
                    authorizationUrl = "https://accounts.example/authorize",
                    tokenUrl = "http://localhost:${server.address.port}/token",
                    redirectUri = "https://app.example/callback"
                ),
                InMemoryTokenStore(),
                InMemorySecretStore(),
                jacksonObjectMapper().findAndRegisterModules()
            )
            val authorization = service.authorizationUrl(GoogleAuthorizationRequest("tenant-a", "user-a"))
            val state = URI.create(authorization.authorizationUrl).rawQuery.split('&')
                .associate { part -> part.substringBefore('=') to URLDecoder.decode(part.substringAfter('='), Charsets.UTF_8) }
                .getValue("state")
            service.complete("authorization-code", state)
            assertThrows(IllegalArgumentException::class.java) { service.complete("authorization-code", state) }

            assertEquals("access-2", service.accessToken("tenant-a", "user-a"))
            assertNull(service.accessToken("tenant-b", "user-a"))
            org.junit.jupiter.api.Assertions.assertTrue(forms[0].contains("grant_type=authorization_code"))
            org.junit.jupiter.api.Assertions.assertTrue(forms[1].contains("grant_type=refresh_token"))
        } finally {
            server.stop(0)
        }
    }
}
