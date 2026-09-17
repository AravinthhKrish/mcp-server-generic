package com.example.mcp

import com.example.mcp.auth.FileTokenStore
import com.example.mcp.auth.OAuthTokenRecord
import com.example.mcp.cache.FileCacheService
import com.example.mcp.storage.CacheStorageProperties
import com.example.mcp.storage.TokenStorageProperties
import com.example.mcp.storage.SecretStorageProperties
import com.example.mcp.auth.EncryptedFileSecretStore
import com.example.mcp.domain.drive.FileReelUploadJobStore
import com.example.mcp.domain.drive.ReelUploadJob
import com.example.mcp.domain.drive.ReelUploadState
import com.example.mcp.storage.JobStorageProperties
import com.example.mcp.storage.UploadSessionStorageProperties
import com.example.mcp.domain.drive.DriveUploadSession
import com.example.mcp.domain.drive.FileDriveUploadSessionStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class PersistenceStoresTest {
    @TempDir
    lateinit var tempDir: Path

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `token references survive store restart and remain tenant isolated`() {
        val properties = TokenStorageProperties(true, tempDir.resolve("tokens.json").toString())
        FileTokenStore(properties, objectMapper).save(OAuthTokenRecord(
            provider = "google",
            tenantId = "tenant-a",
            userId = "user-a",
            accessTokenRef = "secret://google/access/a",
            refreshTokenRef = "secret://google/refresh/a",
            expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        ))

        val restarted = FileTokenStore(properties, objectMapper)
        assertEquals("secret://google/access/a", restarted.find("google", "tenant-a", "user-a")?.accessTokenRef)
        assertEquals(null, restarted.find("google", "tenant-b", "user-a"))
    }

    @Test
    fun `cache entries survive restart and expiration remains enforced`() {
        val properties = CacheStorageProperties(true, tempDir.resolve("cache.json").toString())
        FileCacheService(properties, objectMapper).put("key", "persisted", Duration.ofMinutes(1))
        assertEquals("persisted", FileCacheService(properties, objectMapper).get<String>("key"))

        FileCacheService(properties, objectMapper).put("expired", "gone", Duration.ofMillis(1))
        Thread.sleep(10)
        assertEquals(null, FileCacheService(properties, objectMapper).get<String>("expired"))
    }

    @Test
    fun `OAuth secrets are encrypted and survive restart`() {
        val file = tempDir.resolve("secrets.json")
        val key = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val properties = SecretStorageProperties(true, file.toString(), key)
        val reference = EncryptedFileSecretStore(properties, objectMapper).put("refresh-token-value")
        val persisted = java.nio.file.Files.readString(file)
        org.junit.jupiter.api.Assertions.assertFalse(persisted.contains("refresh-token-value"))
        assertEquals("refresh-token-value", EncryptedFileSecretStore(properties, objectMapper).get(reference))
    }

    @Test
    fun `reel job progress survives store restart`() {
        val properties = JobStorageProperties(true, tempDir.resolve("jobs.json").toString())
        val now = Instant.parse("2026-09-16T00:00:00Z")
        val job = ReelUploadJob("job", "key", "reel.mp4", "folder", "upload", 262144,
            500000, ReelUploadState.IN_PROGRESS, createdAt = now, updatedAt = now)
        FileReelUploadJobStore(properties, objectMapper).save(job)
        assertEquals(262144, FileReelUploadJobStore(properties, objectMapper).find("job")?.uploadedBytes)
        assertEquals("job", FileReelUploadJobStore(properties, objectMapper).findByIdempotencyKey("key")?.jobId)
    }

    @Test
    fun `provider upload session survives store restart without credentials`() {
        val properties = UploadSessionStorageProperties(true, tempDir.resolve("uploads.json").toString())
        FileDriveUploadSessionStore(properties, objectMapper).save(DriveUploadSession(
            id = "upload", uploadUri = "https://upload.example/session", totalSizeBytes = 500000,
            uploadedBytes = 262144
        ))
        val restored = FileDriveUploadSessionStore(properties, objectMapper).find("upload")!!
        assertEquals("https://upload.example/session", restored.uploadUri)
        assertEquals(262144, restored.uploadedBytes)
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.readString(tempDir.resolve("uploads.json")).contains("access-token"))
    }
}
