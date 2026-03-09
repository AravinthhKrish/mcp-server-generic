package com.example.mcp.mcp

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@EnableConfigurationProperties(ApiAuthProperties::class)
class ApiMcpAuthFilter(
    private val authProperties: ApiAuthProperties
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        if (!path.startsWith("/api/mcp")) {
            return chain.filter(exchange)
        }

        val authHeader = exchange.request.headers.getFirst("Authorization").orEmpty()
        val bearerPrefix = "Bearer "
        val token = if (authHeader.startsWith(bearerPrefix)) authHeader.removePrefix(bearerPrefix) else ""

        if (token != authProperties.token) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        return chain.filter(exchange)
    }
}
