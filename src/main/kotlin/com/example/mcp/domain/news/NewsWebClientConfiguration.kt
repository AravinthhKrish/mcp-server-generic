package com.example.mcp.domain.news

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class NewsWebClientConfiguration {
    @Bean("newsWebClient")
    fun newsWebClient(builder: WebClient.Builder, properties: NewsProperties): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeoutMs.toInt())
            .responseTimeout(Duration.ofMillis(properties.readTimeoutMs))
            .followRedirect(true)
            .compress(true)
            .doOnConnected { connection ->
                val timeoutSeconds = (properties.readTimeoutMs / 1000).coerceAtLeast(1)
                connection.addHandlerLast(ReadTimeoutHandler(timeoutSeconds))
                connection.addHandlerLast(WriteTimeoutHandler(timeoutSeconds))
            }
            .headers { headers ->
                headers.add(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                headers.add(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                headers.add(
                    HttpHeaders.ACCEPT,
                    "application/json, application/rss+xml, application/atom+xml, application/xml, text/xml, text/html"
                )
            }

        return builder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
