package com.example.mcp.domain.market

import com.example.mcp.domain.Quote
import com.example.mcp.mcp.MarketQuoteInput
import org.springframework.stereotype.Component
import java.time.Instant

interface MarketDataAdapter {
    fun quote(input: MarketQuoteInput): Quote
}

@Component
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
