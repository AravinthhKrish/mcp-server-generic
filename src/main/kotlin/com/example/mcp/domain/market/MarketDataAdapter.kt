package com.example.mcp.domain.market

import com.example.mcp.domain.Quote
import com.example.mcp.mcp.MarketQuoteInput
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant

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
    private val client = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .build()

    override fun quote(input: MarketQuoteInput): Quote {
        require(properties.baseUrl.isNotBlank()) {
            "integrations.market.base-url must be configured when integrations.market.enabled=true"
        }

        val payload = linkedMapOf<String, Any?>(
            "symbol" to input.symbol,
            "providerPreference" to (input.providerPreference ?: "kite"),
            "assetClass" to input.assetClass
        ).filterValues { it != null }

        val body = client.post()
            .uri(properties.quotePath)
            .contentType(MediaType.APPLICATION_JSON)
            .headers { headers ->
                if (properties.authToken.isNotBlank()) {
                    headers.setBearerAuth(properties.authToken)
                }
            }
            .body(payload)
            .retrieve()
            .body(String::class.java)
            ?: "{}"

        return parseQuote(body, input)
    }

    private fun parseQuote(body: String, input: MarketQuoteInput): Quote {
        val root = objectMapper.readTree(body)

        val quoteNode = when {
            root.has("quote") -> root.path("quote")
            root.has("result") && root.path("result").isTextual -> {
                runCatching { objectMapper.readTree(root.path("result").asText()) }.getOrNull()?.path("quote")
                    ?: root.path("result")
            }
            root.has("result") && root.path("result").isObject -> {
                val result = root.path("result")
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
