package com.example.mcp.auth

import org.springframework.stereotype.Component

interface TokenStore {
    fun find(provider: String, tenantId: String, userId: String): OAuthTokenRecord?
    fun save(record: OAuthTokenRecord)
}

@Component
class InMemoryTokenStore : TokenStore {
    private val records = mutableMapOf<String, OAuthTokenRecord>()

    override fun find(provider: String, tenantId: String, userId: String): OAuthTokenRecord? {
        return records[key(provider, tenantId, userId)]
    }

    override fun save(record: OAuthTokenRecord) {
        records[key(record.provider, record.tenantId, record.userId)] = record
    }

    private fun key(provider: String, tenantId: String, userId: String) = "$provider::$tenantId::$userId"
}
