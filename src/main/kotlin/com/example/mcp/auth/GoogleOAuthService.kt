package com.example.mcp.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ConfigurationProperties(prefix = "integrations.google-oauth")
data class GoogleOAuthProperties(
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val authorizationUrl: String = "https://accounts.google.com/o/oauth2/v2/auth",
    val tokenUrl: String = "https://oauth2.googleapis.com/token",
    val redirectUri: String = "http://localhost:8080/oauth/google/callback",
    val scopes: List<String> = listOf("https://www.googleapis.com/auth/drive.file")
)

data class GoogleAuthorizationRequest(
    @field:jakarta.validation.constraints.NotBlank val tenantId: String,
    @field:jakarta.validation.constraints.NotBlank val userId: String
)
data class GoogleAuthorizationResponse(val authorizationUrl: String)
data class GoogleConnectionResponse(val connected: Boolean, val tenantId: String, val userId: String)

@Service
class GoogleOAuthService(
    private val properties: GoogleOAuthProperties,
    private val tokenStore: TokenStore,
    private val secretStore: SecretStore,
    private val objectMapper: ObjectMapper
) {
    private data class PendingAuthorization(val tenantId: String, val userId: String, val expiresAt: Instant)
    private val pending = ConcurrentHashMap<String, PendingAuthorization>()
    private val refreshLocks = ConcurrentHashMap<String, Any>()
    private val client = RestClient.builder().build()

    fun authorizationUrl(request: GoogleAuthorizationRequest): GoogleAuthorizationResponse {
        requireConfigured()
        require(request.tenantId.isNotBlank() && request.userId.isNotBlank()) { "tenantId and userId are required" }
        val state = randomState()
        pending[state] = PendingAuthorization(request.tenantId, request.userId, Instant.now().plusSeconds(600))
        val url = UriComponentsBuilder.fromUriString(properties.authorizationUrl)
            .queryParam("client_id", properties.clientId)
            .queryParam("redirect_uri", properties.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", properties.scopes.joinToString(" "))
            .queryParam("access_type", "offline")
            .queryParam("prompt", "consent")
            .queryParam("state", state)
            .build().encode().toUriString()
        return GoogleAuthorizationResponse(url)
    }

    fun complete(code: String, state: String): GoogleConnectionResponse {
        requireConfigured()
        val context = pending.remove(state) ?: throw IllegalArgumentException("Invalid or already-used OAuth state")
        require(Instant.now().isBefore(context.expiresAt)) { "OAuth state expired" }
        val token = exchange(mapOf(
            "code" to code,
            "client_id" to properties.clientId,
            "client_secret" to properties.clientSecret,
            "redirect_uri" to properties.redirectUri,
            "grant_type" to "authorization_code"
        ))
        saveToken(context.tenantId, context.userId, token, null)
        return GoogleConnectionResponse(true, context.tenantId, context.userId)
    }

    fun accessToken(tenantId: String, userId: String): String? {
        if (!properties.enabled) return null
        val key = "$tenantId::$userId"
        synchronized(refreshLocks.computeIfAbsent(key) { Any() }) {
            val record = tokenStore.find("google-drive", tenantId, userId) ?: return null
            if (record.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
                return secretStore.get(record.accessTokenRef)
            }
            val refreshToken = secretStore.get(record.refreshTokenRef)
                ?: throw IllegalStateException("Google refresh token is unavailable")
            val refreshed = exchange(mapOf(
                "refresh_token" to refreshToken,
                "client_id" to properties.clientId,
                "client_secret" to properties.clientSecret,
                "grant_type" to "refresh_token"
            ))
            return saveToken(tenantId, userId, refreshed, record).let { secretStore.get(it.accessTokenRef) }
        }
    }

    private fun saveToken(
        tenantId: String,
        userId: String,
        token: com.fasterxml.jackson.databind.JsonNode,
        previous: OAuthTokenRecord?
    ): OAuthTokenRecord {
        val access = token.path("access_token").asText("")
        require(access.isNotBlank()) { "OAuth provider did not return an access token" }
        val accessRef = secretStore.put(access, previous?.accessTokenRef)
        val returnedRefresh = token.path("refresh_token").asText("")
        val refreshRef = when {
            returnedRefresh.isNotBlank() -> secretStore.put(returnedRefresh, previous?.refreshTokenRef)
            previous != null -> previous.refreshTokenRef
            else -> throw IllegalArgumentException("OAuth provider did not return a refresh token")
        }
        val record = OAuthTokenRecord(
            provider = "google-drive",
            tenantId = tenantId,
            userId = userId,
            accessTokenRef = accessRef,
            refreshTokenRef = refreshRef,
            expiresAt = Instant.now().plusSeconds(token.path("expires_in").asLong(3600))
        )
        tokenStore.save(record)
        return record
    }

    private fun exchange(values: Map<String, String>): com.fasterxml.jackson.databind.JsonNode {
        val form = LinkedMultiValueMap<String, String>()
        values.forEach(form::add)
        val response = client.post().uri(properties.tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve().body(String::class.java) ?: "{}"
        return objectMapper.readTree(response)
    }

    private fun requireConfigured() {
        require(properties.enabled) { "Google OAuth is disabled" }
        require(properties.clientId.isNotBlank() && properties.clientSecret.isNotBlank()) {
            "Google OAuth client credentials are not configured"
        }
    }

    private fun randomState(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
