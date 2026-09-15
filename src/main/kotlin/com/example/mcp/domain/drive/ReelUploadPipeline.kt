package com.example.mcp.domain.drive

import com.example.mcp.domain.DriveFile
import com.example.mcp.mcp.DriveCreateFolderInput
import com.example.mcp.mcp.DriveStartResumableUploadInput
import com.example.mcp.mcp.DriveUploadChunkInput
import com.example.mcp.storage.JobStorageProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ReelUploadState { IN_PROGRESS, COMPLETE }

data class ReelUploadJob(
    val jobId: String,
    val idempotencyKey: String,
    val name: String,
    val folderId: String?,
    val uploadId: String,
    val uploadedBytes: Long,
    val totalSizeBytes: Long,
    val state: ReelUploadState,
    val file: DriveFile? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

interface ReelUploadJobStore {
    fun find(jobId: String): ReelUploadJob?
    fun findByIdempotencyKey(key: String): ReelUploadJob?
    fun save(job: ReelUploadJob): ReelUploadJob
}

@Component
@ConditionalOnProperty(prefix = "storage.jobs", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class InMemoryReelUploadJobStore : ReelUploadJobStore {
    private val jobs = ConcurrentHashMap<String, ReelUploadJob>()
    override fun find(jobId: String): ReelUploadJob? = jobs[jobId]
    override fun findByIdempotencyKey(key: String): ReelUploadJob? = jobs.values.firstOrNull { it.idempotencyKey == key }
    override fun save(job: ReelUploadJob): ReelUploadJob = job.also { jobs[it.jobId] = it }
}

@Component
@ConditionalOnProperty(prefix = "storage.jobs", name = ["enabled"], havingValue = "true")
class FileReelUploadJobStore(
    properties: JobStorageProperties,
    private val objectMapper: ObjectMapper
) : ReelUploadJobStore {
    private val path = Path.of(properties.path).toAbsolutePath().normalize()
    private val jobs = ConcurrentHashMap<String, ReelUploadJob>()

    init {
        if (Files.exists(path)) {
            jobs.putAll(objectMapper.readValue(path.toFile(), object : TypeReference<Map<String, ReelUploadJob>>() {}))
        }
    }

    override fun find(jobId: String): ReelUploadJob? = jobs[jobId]
    override fun findByIdempotencyKey(key: String): ReelUploadJob? = jobs.values.firstOrNull { it.idempotencyKey == key }

    @Synchronized
    override fun save(job: ReelUploadJob): ReelUploadJob {
        jobs[job.jobId] = job
        path.parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        objectMapper.writeValue(temporary.toFile(), jobs.toSortedMap())
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
        return job
    }
}

@Service
class ReelUploadPipeline(
    private val drive: GoogleDriveAdapter,
    private val jobs: ReelUploadJobStore
) {
    @Synchronized
    fun start(input: com.example.mcp.mcp.DriveStartReelUploadInput): ReelUploadJob {
        jobs.findByIdempotencyKey(input.idempotencyKey)?.let { return it }
        val folderId = input.parentFolderId ?: input.folderName?.takeIf(String::isNotBlank)?.let { folderName ->
            drive.createFolder(DriveCreateFolderInput(
                name = folderName,
                accessToken = input.accessToken,
                tenantId = input.tenantId,
                userId = input.userId
            )).id
        }
        val upload = drive.startResumableUpload(DriveStartResumableUploadInput(
            name = input.name,
            totalSizeBytes = input.totalSizeBytes,
            mimeType = input.mimeType,
            parentFolderId = folderId,
            accessToken = input.accessToken,
            tenantId = input.tenantId,
            userId = input.userId
        ))
        val now = Instant.now()
        return jobs.save(ReelUploadJob(
            jobId = UUID.randomUUID().toString(),
            idempotencyKey = input.idempotencyKey,
            name = input.name,
            folderId = folderId,
            uploadId = upload.uploadId,
            uploadedBytes = upload.uploadedBytes,
            totalSizeBytes = upload.totalSizeBytes,
            state = ReelUploadState.IN_PROGRESS,
            createdAt = now,
            updatedAt = now
        ))
    }

    fun uploadChunk(input: com.example.mcp.mcp.DriveUploadReelChunkInput): ReelUploadJob {
        val job = jobs.find(input.jobId) ?: throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Reel upload job not found")
        require(job.state != ReelUploadState.COMPLETE) { "Reel upload is already complete" }
        val upload = drive.uploadChunk(DriveUploadChunkInput(
            uploadId = job.uploadId,
            offset = input.offset,
            contentBase64 = input.contentBase64,
            accessToken = input.accessToken,
            tenantId = input.tenantId,
            userId = input.userId
        ))
        return jobs.save(job.copy(
            uploadedBytes = upload.uploadedBytes,
            state = if (upload.complete) ReelUploadState.COMPLETE else ReelUploadState.IN_PROGRESS,
            file = upload.file,
            updatedAt = Instant.now()
        ))
    }

    fun status(jobId: String): ReelUploadJob = jobs.find(jobId)
        ?: throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Reel upload job not found")
}
