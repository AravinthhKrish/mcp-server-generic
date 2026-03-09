package com.example.mcp.domain.market

import com.example.mcp.domain.Quote
import com.example.mcp.domain.util.parseNodes
import com.example.mcp.domain.util.toDateString
import com.example.mcp.mcp.MarketQuoteInput
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.contains
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.toString

interface MarketDataAdapter {
    suspend fun quote(input: MarketQuoteInput): List<Quote>
}

@Component
@ConditionalOnProperty(prefix = "integrations.market", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class StubMarketDataAdapter : MarketDataAdapter {
    override suspend fun quote(input: MarketQuoteInput): List<Quote> {
        return listOf(
            Quote(
                symbol = input.symbol.uppercase(),
                venue = "XNAS",
                currency = "USD",
                last = 189.42,
                bid = 189.4,
                ask = 189.45,
                change = 1.82,
                changePercent = 0.97,
                timestamp = Instant.now(),
                provider = input.providerPreference ?: "alpha-vantage",
                data = emptyMap()
            )
        )
    }
}

@Component
@ConditionalOnProperty(prefix = "integrations.market", name = ["enabled"], havingValue = "true")
class KiteMcpMarketDataAdapter(
    private val properties: MarketProperties,
    private val objectMapper: ObjectMapper
) : MarketDataAdapter {
    private val MCP_SESSION_HEADER = "mcp-session-id"
    private val client = WebClient.builder()
        .baseUrl(properties.baseUrl.ifBlank { "https://mcp.kite.trade/mcp" })
        .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()
    private val idSeq = AtomicLong(1)

    @Volatile
    private var initialized = false

    @Volatile
    private var mcpSession = ""

    override suspend fun quote(input: MarketQuoteInput): List<Quote> {
        require(properties.baseUrl.isNotBlank()) {
            "integrations.market.base-url must be configured when integrations.market.enabled=true"
        }

        if (!initialized) {
            initializeSession()
            initialized = true
        }

        val availableTools = listTools()
        require(availableTools.contains("search_instruments")) {
            "Remote MCP endpoint does not expose search_instruments tool. Available tools: $availableTools"
        }

        val responseToken = runCatching {
            postRpc(
                method = "tools/call",
                params = mapOf(
                    "name" to "search_instruments",
                    "arguments" to linkedMapOf<String, Any?>(
                        "query" to input.symbol,
                        "filter_on" to (input.providerPreference ?: "tradingsymbol"),
                    ).filterValues { it != null }
                )
            )
        }.getOrDefault(JsonNode.OverwriteMode.ALL)

//        val instrumentToken = parseToken(responseToken.toString()).firstOrNull()
//        val historicalData = runCatching {
//            postRpc(
//                method = "tools/call",
//                params = mapOf(
//                    "name" to "get_historical_data",
//                    "arguments" to linkedMapOf<String, Any?>(
//                        "instrument_token" to instrumentToken.orEmpty(),
//                        "from_date" to Instant.now().minus(7, ChronoUnit.DAYS).toDateString() ,
//                        "to_date" to Instant.now().toDateString(),
//                        "interval" to (input.assetClass ?: "day"),
//                        "continuous" to false,
//                        "oi" to false,
//                    ).filterValues { it != null }
//                )
//            )
//        }.getOrDefault(JsonNode.OverwriteMode.ALL)

        return parseQuotes(responseToken.toString(), input).takeLast(5)
    }

    private suspend fun initializeSession() {
        postRpc(
            method = "initialize",
            params = mapOf(
                "protocolVersion" to "2025-03-26",
                "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
                "clientInfo" to mapOf("name" to "mcp-server-generic", "version" to "0.1.0")
            ),
            session = true
        )
//        postRpc(method = "notifications/initialized")
    }

    private suspend fun listTools(): List<String> {
        val response = postRpc(method = "tools/list")
        val toolsNode = response.path("tools").takeIf { it.isArray }
            ?: response.path("result").path("tools")

        if (!toolsNode.isArray) return emptyList()
        return toolsNode.mapNotNull { it.path("name").asText(null) }
    }

    private suspend fun postRpc(
        method: String,
        params: Any? = null,
        session: Boolean = false
    ): JsonNode {
        val request = linkedMapOf<String, Any?>(
            "jsonrpc" to "2.0",
            "id" to idSeq.getAndIncrement(),
            "method" to method,
            "params" to params
        )

        val response = client.post()
            .headers { headers ->
                if (properties.authToken.isNotBlank()) {
                    headers.setBearerAuth(properties.authToken)
                }
                if (mcpSession.isNotEmpty()) {
                    headers.set(MCP_SESSION_HEADER, mcpSession)
                }
            }
            .bodyValue(request)
            .exchangeToMono { response ->
                response.bodyToMono(object : ParameterizedTypeReference<ByteArray>() {})
                    .map { body ->
                        val sessionId = response.headers().asHttpHeaders().getFirst(MCP_SESSION_HEADER)
                        if (!sessionId.isNullOrBlank() && session) {
                            mcpSession = sessionId
                        }
                        body
                    }

            }
            .awaitSingleOrNull()
            ?: "{}".toByteArray()

        val text = response.toString(StandardCharsets.UTF_8)
        val payload = parseRpcPayload(text)
        val error = payload.path("error")
        if (!error.isMissingNode && !error.isNull) {
            val code = error.path("code").asInt(-1)
            val message = error.path("message").asText("Unknown MCP error")
            throw IllegalStateException("MCP error $code: $message")
        }

        return payload.path("result").takeIf { !it.isMissingNode && !it.isNull } ?: payload
    }

    private fun parseRpcPayload(rawBody: String): JsonNode {
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return objectMapper.readTree(trimmed)
        }

        val sseData = trimmed
            .lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotBlank() && it != "[DONE]" }
            .lastOrNull()
            ?: "{}"

        return objectMapper.readTree(sseData)
    }

    private fun parseToken(body: String): List<String> {
        val instrumentsNode = objectMapper.parseNodes(body)
        if (!instrumentsNode.contains("data")) {
            return emptyList()
        }

        return instrumentsNode.path("data").filter { it.path("instrument_token")!= null }.map { nodes ->
            nodes.path("instrument_token").asText()
        }
    }



    private fun parseQuotes(body: String, input: MarketQuoteInput): List<Quote> {
        val instrumentsNode = objectMapper.parseNodes(body)

        if (!instrumentsNode.isArray) {
            return emptyList()
        }

        return instrumentsNode.map { node ->
            Quote(
                symbol = node.path("tradingsymbol")
                    .asText(
                        node.path("id")
                            .asText(input.symbol.uppercase())
                    ),
                venue = node.path("exchange").asText("NSE"),
                currency = "INR",
                last = node.path("last_price").asDouble(0.0),
                bid = null,
                ask = null,
                change = 0.0,
                changePercent = 0.0,
                timestamp = node.path("listing_date").asInstantOrNow(),
                provider = "kite-mcp",
                data = objectMapper.convertValue(node, object: TypeReference<Map<String, Any>>(){})
            )
        }
    }


    private fun unwrapTextNode(node: JsonNode): JsonNode {
        var current = node
        var depth = 0
        while (current.isTextual && depth < 3) {
            val parsed = runCatching { objectMapper.readTree(current.asText()) }.getOrNull() ?: break
            current = parsed
            depth++
        }
        return current
    }

    private fun JsonNode.asDoubleOrNull(): Double? {
        return when {
            isNumber -> asDouble()
            isTextual -> asText().toDoubleOrNull()
            else -> null
        }
    }

    private fun JsonNode.asInstantOrNow(): Instant {
        return if (isTextual) runCatching { Instant.parse(asText()) }.getOrNull() ?: Instant.now() else Instant.now()
    }
}
