package com.example.mcp.cache

import org.springframework.stereotype.Component
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface CacheService {
    fun <T> get(key: String): T?
    fun put(key: String, value: Any, ttl: Duration)
}

@Component
@ConditionalOnProperty(prefix = "storage.cache", name = ["enabled"], havingValue = "false", matchIfMissing = true)
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

@Component
@ConditionalOnProperty(prefix = "storage.cache", name = ["enabled"], havingValue = "true")
class FileCacheService(
    private val properties: com.example.mcp.storage.CacheStorageProperties,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper
) : CacheService {
    data class StoredEntry(
        val value: com.fasterxml.jackson.databind.JsonNode,
        val type: String,
        val expiresAt: Instant
    )

    private val path = java.nio.file.Path.of(properties.path).toAbsolutePath().normalize()
    private val entries = java.util.concurrent.ConcurrentHashMap<String, StoredEntry>()

    init {
        if (java.nio.file.Files.exists(path)) {
            val type = objectMapper.typeFactory.constructMapType(Map::class.java, String::class.java, StoredEntry::class.java)
            entries.putAll(objectMapper.readValue(path.toFile(), type))
            evictExpired(Instant.now())
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? {
        val entry = entries[key] ?: return null
        if (Instant.now().isAfter(entry.expiresAt)) {
            synchronized(this) {
                entries.remove(key)
                persist()
            }
            return null
        }
        require(entry.type.startsWith("com.example.mcp.") || entry.type.startsWith("java.lang.")) {
            "Unsupported persisted cache type"
        }
        return objectMapper.treeToValue(entry.value, Class.forName(entry.type)) as T
    }

    @Synchronized
    override fun put(key: String, value: Any, ttl: Duration) {
        require(!ttl.isNegative && !ttl.isZero) { "Cache TTL must be positive" }
        entries[key] = StoredEntry(objectMapper.valueToTree(value), value.javaClass.name, Instant.now().plus(ttl))
        evictExpired(Instant.now())
        persist()
    }

    private fun evictExpired(now: Instant) {
        entries.entries.removeIf { now.isAfter(it.value.expiresAt) }
    }

    private fun persist() {
        path.parent?.let(java.nio.file.Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        objectMapper.writeValue(temporary.toFile(), entries.toSortedMap())
        try {
            java.nio.file.Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
