package com.example.mcp.auth

import com.example.mcp.storage.SecretStorageProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface SecretStore {
    fun put(value: String, reference: String? = null): String
    fun get(reference: String): String?
}

@Component
@ConditionalOnProperty(prefix = "storage.secrets", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class InMemorySecretStore : SecretStore {
    private val secrets = ConcurrentHashMap<String, String>()
    override fun put(value: String, reference: String?): String {
        val id = reference ?: "secret://${UUID.randomUUID()}"
        secrets[id] = value
        return id
    }
    override fun get(reference: String): String? = secrets[reference]
}

@Component
@ConditionalOnProperty(prefix = "storage.secrets", name = ["enabled"], havingValue = "true")
class EncryptedFileSecretStore(
    properties: SecretStorageProperties,
    private val objectMapper: ObjectMapper
) : SecretStore {
    private val path = Path.of(properties.path).toAbsolutePath().normalize()
    private val keyBytes = Base64.getDecoder().decode(properties.encryptionKeyBase64)
    private val secrets = ConcurrentHashMap<String, String>()
    private val random = SecureRandom()

    init {
        require(keyBytes.size == 32) { "storage.secrets.encryption-key-base64 must decode to 32 bytes" }
        if (Files.exists(path)) {
            secrets.putAll(objectMapper.readValue(path.toFile(), object : TypeReference<Map<String, String>>() {}))
        }
    }

    @Synchronized
    override fun put(value: String, reference: String?): String {
        val id = reference ?: "secret://${UUID.randomUUID()}"
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        secrets[id] = Base64.getEncoder().encodeToString(nonce + encrypted)
        persist()
        return id
    }

    override fun get(reference: String): String? {
        val payload = secrets[reference]?.let(Base64.getDecoder()::decode) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        return cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
    }

    private fun persist() {
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        objectMapper.writeValue(temporary.toFile(), secrets.toSortedMap())
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
