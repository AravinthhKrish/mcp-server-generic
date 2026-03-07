package com.example.mcp.cache

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

interface CacheService {
    fun <T> get(key: String): T?
    fun put(key: String, value: Any, ttl: Duration)
}

@Component
class InMemoryCacheService : CacheService {
    private val entries = mutableMapOf<String, Pair<Any, Instant>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? {
        val (value, expiresAt) = entries[key] ?: return null
        if (Instant.now().isAfter(expiresAt)) {
            entries.remove(key)
            return null
        }
        return value as T
    }

    override fun put(key: String, value: Any, ttl: Duration) {
        entries[key] = value to Instant.now().plus(ttl)
    }
}
