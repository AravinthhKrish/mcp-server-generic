package com.example.mcp.cache

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface CacheService {
    fun <T> get(key: String): T?
    fun put(key: String, value: Any, ttl: Duration)
}

@Component
class InMemoryCacheService : CacheService {
    private data class CacheEntry(
        val value: Any,
        val expiresAt: Instant
    )

    private val entries = ConcurrentHashMap<String, CacheEntry>()

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? {
        val entry = entries[key] ?: return null
        if (Instant.now().isAfter(entry.expiresAt)) {
            entries.remove(key, entry)
            return null
        }
        return entry.value as T
    }

    override fun put(key: String, value: Any, ttl: Duration) {
        val now = Instant.now()
        entries[key] = CacheEntry(value = value, expiresAt = now.plus(ttl))
        evictExpired(now)
    }

    private fun evictExpired(now: Instant) {
        entries.entries.removeIf { (_, entry) -> now.isAfter(entry.expiresAt) }
    }

    internal fun size(): Int = entries.size
}
