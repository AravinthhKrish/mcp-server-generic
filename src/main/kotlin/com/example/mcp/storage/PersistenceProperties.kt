package com.example.mcp.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage.tokens")
data class TokenStorageProperties(
    val enabled: Boolean = false,
    val path: String = "./data/oauth-token-references.json"
)

@ConfigurationProperties(prefix = "storage.cache")
data class CacheStorageProperties(
    val enabled: Boolean = false,
    val path: String = "./data/cache.json"
)

@ConfigurationProperties(prefix = "storage.secrets")
data class SecretStorageProperties(
    val enabled: Boolean = false,
    val path: String = "./data/oauth-secrets.json",
    val encryptionKeyBase64: String = ""
)

@ConfigurationProperties(prefix = "storage.jobs")
data class JobStorageProperties(
    val enabled: Boolean = false,
    val path: String = "./data/reel-upload-jobs.json"
)

@ConfigurationProperties(prefix = "storage.upload-sessions")
data class UploadSessionStorageProperties(
    val enabled: Boolean = false,
    val path: String = "./data/drive-upload-sessions.json"
)
