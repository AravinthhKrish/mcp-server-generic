package com.example.mcp.domain.drive

import com.example.mcp.domain.DriveFile
import com.example.mcp.storage.UploadSessionStorageProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

data class DriveUploadSession(
    val id: String,
    val uploadUri: String,
    val totalSizeBytes: Long,
    val uploadedBytes: Long = 0,
    val file: DriveFile? = null
)

interface DriveUploadSessionStore {
    fun find(id: String): DriveUploadSession?
    fun save(session: DriveUploadSession): DriveUploadSession
}

@Component
@ConditionalOnProperty(prefix = "storage.upload-sessions", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class InMemoryDriveUploadSessionStore : DriveUploadSessionStore {
    private val sessions = ConcurrentHashMap<String, DriveUploadSession>()
    override fun find(id: String): DriveUploadSession? = sessions[id]
    override fun save(session: DriveUploadSession): DriveUploadSession = session.also { sessions[it.id] = it }
}

@Component
@ConditionalOnProperty(prefix = "storage.upload-sessions", name = ["enabled"], havingValue = "true")
class FileDriveUploadSessionStore(
    properties: UploadSessionStorageProperties,
    private val objectMapper: ObjectMapper
) : DriveUploadSessionStore {
    private val path = Path.of(properties.path).toAbsolutePath().normalize()
    private val sessions = ConcurrentHashMap<String, DriveUploadSession>()

    init {
        if (Files.exists(path)) {
            sessions.putAll(objectMapper.readValue(path.toFile(), object : TypeReference<Map<String, DriveUploadSession>>() {}))
        }
    }

    override fun find(id: String): DriveUploadSession? = sessions[id]

    @Synchronized
    override fun save(session: DriveUploadSession): DriveUploadSession {
        sessions[session.id] = session
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        objectMapper.writeValue(temporary.toFile(), sessions.toSortedMap())
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
        return session
    }
}
