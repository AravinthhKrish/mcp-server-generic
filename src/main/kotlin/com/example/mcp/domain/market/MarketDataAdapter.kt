package com.example.mcp.domain.market

import com.example.mcp.domain.Quote
import com.example.mcp.mcp.MarketQuoteInput
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

interface MarketDataAdapter {
    fun quote(input: MarketQuoteInput): Quote
}

@Component
@ConditionalOnProperty(prefix = "integrations.market", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class StubMarketDataAdapter : MarketDataAdapter {
    override fun quote(input: MarketQuoteInput): Quote {
        return Quote(
            symbol = input.symbol.uppercase(),
            venue = "XNAS",
            currency = "USD",
            last = 189.42,
            bid = 189.4,
            ask = 189.45,
            change = 1.82,
            changePercent = 0.97,
            timestamp = Instant.now(),
            provider = input.providerPreference ?: "alpha-vantage"
        )
    }
}

@Component
@ConditionalOnProperty(prefix = "integrations.market", name = ["enabled"], havingValue = "true")
class KiteMcpMarketDataAdapter(
    private val properties: MarketProperties,
    private val objectMapper: ObjectMapper
) : MarketDataAdapter {
    private val client = WebClient.builder()
        .baseUrl(properties.baseUrl.ifBlank { "https://mcp.kite.trade/mcp" })
        .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()
    private val idSeq = AtomicLong(1)
    @Volatile
    private var initialized = false

    override fun quote(input: MarketQuoteInput): Quote {
        require(properties.baseUrl.isNotBlank()) {
            "integrations.market.base-url must be configured when integrations.market.enabled=true"
        }

        if (!initialized) {
            initializeSession()
            initialized = true
        }

        val availableTools = listTools()
        require(availableTools.contains("market.quote")) {
            "Remote MCP endpoint does not expose market.quote tool. Available tools: $availableTools"
        }

        val response = postRpc(
            method = "tools/call",
            params = mapOf(
                "name" to "market.quote",
                "arguments" to linkedMapOf<String, Any?>(
                    "symbol" to input.symbol,
                    "providerPreference" to (input.providerPreference ?: "kite"),
                    "assetClass" to input.assetClass
                ).filterValues { it != null }
            )
        )

        return parseQuote(response.toString(), input)
    }

    private fun initializeSession() {
        postRpc(
            method = "initialize",
            params = mapOf(
                "protocolVersion" to "2025-03-26",
                "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
                "clientInfo" to mapOf("name" to "mcp-server-generic", "version" to "0.1.0")
            )
        )

        postRpc(method = "notifications/initialized")
    }

    private fun listTools(): List<String> {
        val response = postRpc(method = "tools/list")
        val toolsNode = response.path("tools").takeIf { it.isArray }
            ?: response.path("result").path("tools")

        if (!toolsNode.isArray) return emptyList()
        return toolsNode.mapNotNull { it.path("name").asText(null) }
    }

    private fun postRpc(method: String, params: Any? = null): JsonNode {
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
            }
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ByteArray::class.java)
            .block()
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

    private fun parseQuote(body: String, input: MarketQuoteInput): Quote {
        val root = unwrapTextNode(objectMapper.readTree(body))

        val quoteNode = when {
            root.has("quote") -> unwrapTextNode(root.path("quote"))
            root.has("result") && root.path("result").isTextual -> {
                val parsedResult = unwrapTextNode(root.path("result"))
                if (parsedResult.has("quote")) parsedResult.path("quote") else parsedResult
            }
            root.has("result") && root.path("result").isObject -> {
                val result = unwrapTextNode(root.path("result"))
                if (result.has("quote")) result.path("quote") else result
            }
            else -> root
        }

        return Quote(
            symbol = quoteNode.path("symbol").asText(input.symbol.uppercase()),
            venue = quoteNode.path("venue").asText("NSE"),
            currency = quoteNode.path("currency").asText("INR"),
            last = quoteNode.path("last").asDouble(0.0),
            bid = quoteNode.path("bid").asDoubleOrNull(),
            ask = quoteNode.path("ask").asDoubleOrNull(),
            change = quoteNode.path("change").asDouble(0.0),
            changePercent = quoteNode.path("changePercent").asDouble(0.0),
            timestamp = quoteNode.path("timestamp").asInstantOrNow(),
            provider = quoteNode.path("provider").asText("kite-mcp")
        )
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
