package com.example.mcp.auth

import java.time.Instant

data class AccessContext(
    val tenantId: String,
    val userId: String,
    val scopes: Set<String>
)

data class OAuthTokenRecord(
    val provider: String,
    val tenantId: String,
    val userId: String,
    val accessTokenRef: String,
    val refreshTokenRef: String,
    val expiresAt: Instant
)
