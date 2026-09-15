package com.example.mcp.domain.gmail

import com.example.mcp.mcp.GmailGetThreadInput
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class ApiGmailAdapterTest {
    @Test
    fun `thread endpoint normalizes all messages and sends bearer token`() {
        val authorization = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/users/me/threads/thread-1") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            val body = """{"messages":[
              {"id":"m1","threadId":"thread-1","internalDate":"1000","snippet":"first","labelIds":["INBOX"],
               "payload":{"headers":[{"name":"From","value":"a@example.com"},{"name":"To","value":"b@example.com"},{"name":"Subject","value":"Hello"}]}},
              {"id":"m2","threadId":"thread-1","internalDate":"2000","snippet":"second","labelIds":[],
               "payload":{"headers":[{"name":"From","value":"b@example.com"},{"name":"To","value":"a@example.com"},{"name":"Subject","value":"Re: Hello"}]}}
            ]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        try {
            val adapter = ApiGmailAdapter(GmailProperties(
                enabled = true,
                baseUrl = "http://localhost:${server.address.port}",
                accessToken = "gmail-token"
            ), ObjectMapper())
            val messages = adapter.getThread(GmailGetThreadInput("thread-1"))
            assertEquals("Bearer gmail-token", authorization.get())
            assertEquals(listOf("m1", "m2"), messages.map { it.id })
            assertEquals("Re: Hello", messages[1].subject)
        } finally {
            server.stop(0)
        }
    }
}
