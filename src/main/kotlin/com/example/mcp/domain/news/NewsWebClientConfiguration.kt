package com.example.mcp.domain.news

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.PrematureCloseException
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeoutException

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
                connection.addHandlerLast(ReadTimeoutHandler(timeoutSeconds.toInt()))
                connection.addHandlerLast(WriteTimeoutHandler(timeoutSeconds.toInt()))
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
            .filter(retryFilter(properties.maxRetries))
            .build()
    }

    private fun retryFilter(maxRetries: Int): ExchangeFilterFunction {
        val retries = maxRetries.coerceAtLeast(0)
        return ExchangeFilterFunction { request, next ->
            if (retries == 0) {
                next.exchange(request)
            } else {
                next.exchange(request)
                    .retryWhen(
                        Retry.backoff(retries.toLong(), Duration.ofMillis(300))
                            .filter(::isTransientError)
                    )
            }
        }
    }

    private fun isTransientError(error: Throwable): Boolean {
        val message = error.toString().lowercase()
        return error is TimeoutException ||
            error is ReadTimeoutException ||
            error is PrematureCloseException ||
            error is IOException ||
            message.contains("timeout") ||
            message.contains("prematureclose") ||
            message.contains("connection reset")
    }
}
