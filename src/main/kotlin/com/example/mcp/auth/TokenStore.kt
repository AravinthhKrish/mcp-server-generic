package com.example.mcp.auth

import org.springframework.stereotype.Component
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

interface TokenStore {
    fun find(provider: String, tenantId: String, userId: String): OAuthTokenRecord?
    fun save(record: OAuthTokenRecord)
}

@Component
@ConditionalOnProperty(prefix = "storage.tokens", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class InMemoryTokenStore : TokenStore {
    private val records = java.util.concurrent.ConcurrentHashMap<String, OAuthTokenRecord>()

    override fun find(provider: String, tenantId: String, userId: String): OAuthTokenRecord? {
        return records[key(provider, tenantId, userId)]
    }

    override fun save(record: OAuthTokenRecord) {
        records[key(record.provider, record.tenantId, record.userId)] = record
    }

    private fun key(provider: String, tenantId: String, userId: String) = "$provider::$tenantId::$userId"
}

@Component
@ConditionalOnProperty(prefix = "storage.tokens", name = ["enabled"], havingValue = "true")
class FileTokenStore(
    private val properties: com.example.mcp.storage.TokenStorageProperties,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper
) : TokenStore {
    private val path = java.nio.file.Path.of(properties.path).toAbsolutePath().normalize()
    private val records = java.util.concurrent.ConcurrentHashMap<String, OAuthTokenRecord>()

    init {
        if (java.nio.file.Files.exists(path)) {
            val type = objectMapper.typeFactory.constructMapType(Map::class.java, String::class.java, OAuthTokenRecord::class.java)
            records.putAll(objectMapper.readValue(path.toFile(), type))
        }
    }

    override fun find(provider: String, tenantId: String, userId: String): OAuthTokenRecord? =
        records[key(provider, tenantId, userId)]

    @Synchronized
    override fun save(record: OAuthTokenRecord) {
        records[key(record.provider, record.tenantId, record.userId)] = record
        persist()
    }

    private fun persist() {
        path.parent?.let(java.nio.file.Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        objectMapper.writeValue(temporary.toFile(), records.toSortedMap())
        try {
            java.nio.file.Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun key(provider: String, tenantId: String, userId: String) = "$provider::$tenantId::$userId"
}
