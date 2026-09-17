package com.example.mcp.domain.drive

import com.example.mcp.domain.DriveFile
import com.example.mcp.mcp.DriveCreateFolderInput
import com.example.mcp.mcp.DriveGetFileMetadataInput
import com.example.mcp.mcp.DriveSearchFilesInput
import com.example.mcp.mcp.DriveUploadFileInput
import com.example.mcp.mcp.DriveStartResumableUploadInput
import com.example.mcp.mcp.DriveUploadChunkInput
import com.example.mcp.mcp.DriveResumableUploadOutput
import com.example.mcp.mcp.DriveReadFileTextInput
import com.example.mcp.mcp.DriveReadFileTextOutput
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.Base64
import java.util.Optional

interface GoogleDriveAdapter {
    fun searchFiles(input: DriveSearchFilesInput): Pair<List<DriveFile>, String?>
    fun createFolder(input: DriveCreateFolderInput): DriveFile
    fun uploadFile(input: DriveUploadFileInput): DriveFile
    fun getFileMetadata(input: DriveGetFileMetadataInput): DriveFile
    fun startResumableUpload(input: DriveStartResumableUploadInput): DriveResumableUploadOutput
    fun uploadChunk(input: DriveUploadChunkInput): DriveResumableUploadOutput
    fun uploadStatus(uploadId: String): DriveResumableUploadOutput
    fun readFileText(input: DriveReadFileTextInput): DriveReadFileTextOutput
}

@Component
@ConditionalOnProperty(prefix = "integrations.drive", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class StubGoogleDriveAdapter : GoogleDriveAdapter {
    private val files = java.util.concurrent.ConcurrentHashMap<String, DriveFile>()
    private val contents = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val uploads = java.util.concurrent.ConcurrentHashMap<String, StubUpload>()

    private data class StubUpload(
        val id: String,
        val name: String,
        val mimeType: String,
        val parentFolderId: String?,
        val totalSizeBytes: Long,
        val bytes: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream(),
        var file: DriveFile? = null
    )
    override fun searchFiles(input: DriveSearchFilesInput): Pair<List<DriveFile>, String?> {
        val sample = DriveFile(
            id = "file_001",
            name = "Q4 Board Deck",
            mimeType = "application/vnd.google-apps.presentation",
            owners = listOf("finance@company.example"),
            modifiedTime = Instant.now(),
            webViewLink = "https://drive.google.com/file/d/file_001/view",
            parents = listOf("folder_root")
        )
        return listOf(sample) to null
    }

    override fun createFolder(input: DriveCreateFolderInput): DriveFile {
        val id = java.util.UUID.randomUUID().toString()
        return DriveFile(
            id = id,
            name = input.name,
            mimeType = "application/vnd.google-apps.folder",
            owners = listOf("creator@company.example"),
            modifiedTime = Instant.now(),
            webViewLink = "https://drive.google.com/drive/folders/$id",
            parents = listOfNotNull(input.parentFolderId)
        ).also { files[id] = it }
    }

    override fun uploadFile(input: DriveUploadFileInput): DriveFile {
        val id = java.util.UUID.randomUUID().toString()
        val content = Base64.getDecoder().decode(input.contentBase64)
        return DriveFile(
            id = id,
            name = input.name,
            mimeType = input.mimeType,
            owners = listOf("uploader@company.example"),
            modifiedTime = Instant.now(),
            webViewLink = "https://drive.google.com/file/d/$id/view",
            parents = listOfNotNull(input.parentFolderId),
            sizeBytes = content.size.toLong()
        ).also {
            files[id] = it
            contents[id] = content
        }
    }

    override fun getFileMetadata(input: DriveGetFileMetadataInput): DriveFile {
        return files[input.fileId] ?: throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "File not found")
    }

    override fun startResumableUpload(input: DriveStartResumableUploadInput): DriveResumableUploadOutput {
        val id = java.util.UUID.randomUUID().toString()
        uploads[id] = StubUpload(id, input.name, input.mimeType, input.parentFolderId, input.totalSizeBytes)
        return uploadStatus(id)
    }

    override fun uploadChunk(input: DriveUploadChunkInput): DriveResumableUploadOutput {
        val upload = uploads[input.uploadId] ?: throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Upload session not found")
        synchronized(upload) {
            require(upload.file == null) { "Upload is already complete" }
            require(input.offset == upload.bytes.size().toLong()) { "Chunk offset does not match next expected byte" }
            val chunk = Base64.getDecoder().decode(input.contentBase64)
            require(chunk.size <= 8 * 1024 * 1024) { "Chunk exceeds the 8 MiB limit" }
            require(input.offset + chunk.size <= upload.totalSizeBytes) { "Chunk exceeds declared upload size" }
            upload.bytes.write(chunk)
            if (upload.bytes.size().toLong() == upload.totalSizeBytes) {
                val fileId = java.util.UUID.randomUUID().toString()
                upload.file = DriveFile(
                    id = fileId,
                    name = upload.name,
                    mimeType = upload.mimeType,
                    owners = listOf("uploader@company.example"),
                    modifiedTime = Instant.now(),
                    webViewLink = "https://drive.google.com/file/d/$fileId/view",
                    parents = listOfNotNull(upload.parentFolderId),
                    sizeBytes = upload.totalSizeBytes
                ).also {
                    files[fileId] = it
                    contents[fileId] = upload.bytes.toByteArray()
                }
            }
        }
        return uploadStatus(input.uploadId)
    }

    override fun uploadStatus(uploadId: String): DriveResumableUploadOutput {
        val upload = uploads[uploadId] ?: throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Upload session not found")
        return DriveResumableUploadOutput(
            uploadId = upload.id,
            uploadedBytes = upload.bytes.size().toLong(),
            totalSizeBytes = upload.totalSizeBytes,
            complete = upload.file != null,
            file = upload.file,
            source = "stub-google-drive"
        )
    }

    override fun readFileText(input: DriveReadFileTextInput): DriveReadFileTextOutput {
        val file = files[input.fileId] ?: throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "File not found")
        val fullText = contents[input.fileId]?.toString(Charsets.UTF_8).orEmpty()
        return DriveReadFileTextOutput(
            fileId = file.id,
            mimeType = file.mimeType,
            text = fullText.take(input.maxCharacters),
            truncated = fullText.length > input.maxCharacters,
            source = "stub-google-drive"
        )
    }

}

@Component
@ConditionalOnProperty(prefix = "integrations.drive", name = ["enabled"], havingValue = "true")
class ApiGoogleDriveAdapter(
    private val properties: DriveProperties,
    private val objectMapper: ObjectMapper,
    private val oauthService: com.example.mcp.auth.GoogleOAuthService? = null,
    private val uploadSessionStore: DriveUploadSessionStore = InMemoryDriveUploadSessionStore()
) : GoogleDriveAdapter {
    private val uploadLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val resumableHttpClient = java.net.http.HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofMillis(properties.connectTimeoutMs))
        .build()
    private val driveClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .requestFactory(requestFactory())
        .requestInterceptor(::executeWithRetry)
        .build()

    private val uploadClient = RestClient.builder()
        .baseUrl(properties.uploadBaseUrl)
        .requestFactory(requestFactory())
        .requestInterceptor(::executeWithRetry)
        .build()

    private fun requestFactory(): org.springframework.http.client.JdkClientHttpRequestFactory {
        require(properties.connectTimeoutMs > 0 && properties.readTimeoutMs > 0) {
            "Drive timeouts must be positive"
        }
        val client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(properties.connectTimeoutMs))
            .build()
        return org.springframework.http.client.JdkClientHttpRequestFactory(client).apply {
            setReadTimeout(java.time.Duration.ofMillis(properties.readTimeoutMs))
        }
    }

    private fun executeWithRetry(
        request: org.springframework.http.HttpRequest,
        body: ByteArray,
        execution: org.springframework.http.client.ClientHttpRequestExecution
    ): org.springframework.http.client.ClientHttpResponse {
        var attempt = 0
        while (true) {
            val response = execution.execute(request, body)
            if (attempt >= properties.maxRetries || (response.statusCode.value() != 429 && !response.statusCode.is5xxServerError)) {
                return response
            }
            response.close()
            attempt++
            sleepBeforeRetry(attempt)
        }
    }

    override fun searchFiles(input: DriveSearchFilesInput): Pair<List<DriveFile>, String?> {
        val accessToken = requireAccessToken(input.accessToken, input.tenantId, input.userId)
        val query = buildSearchQuery(input)
        val responseBody = driveClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/files")
                    .queryParam("q", query)
                    .queryParam("pageSize", input.pageSize)
                    .queryParam("fields", "files(id,name,mimeType,owners(emailAddress,displayName),modifiedTime,webViewLink,parents,size),nextPageToken")
                    .queryParam("supportsAllDrives", true)
                    .queryParam("includeItemsFromAllDrives", true)
                    .queryParamIfPresent("pageToken", Optional.ofNullable(input.pageToken))
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body(String::class.java)
            ?: "{}"

        val root = objectMapper.readTree(responseBody)
        val files = root.path("files")
            .takeIf(JsonNode::isArray)
            ?.map(::toDriveFile)
            ?: emptyList()

        return files to root.path("nextPageToken").asText(null)
    }

    override fun createFolder(input: DriveCreateFolderInput): DriveFile {
        val accessToken = requireAccessToken(input.accessToken, input.tenantId, input.userId)
        val payload = mutableMapOf<String, Any>(
            "name" to input.name,
            "mimeType" to "application/vnd.google-apps.folder"
        )
        if (!input.parentFolderId.isNullOrBlank()) {
            payload["parents"] = listOf(input.parentFolderId)
        }

        val responseBody = driveClient.post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/files")
                    .queryParam("fields", "id,name,mimeType,owners(emailAddress,displayName),modifiedTime,webViewLink,parents,size")
                    .queryParam("supportsAllDrives", true)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(String::class.java)
            ?: "{}"

        return toDriveFile(objectMapper.readTree(responseBody))
    }

    override fun uploadFile(input: DriveUploadFileInput): DriveFile {
        val accessToken = requireAccessToken(input.accessToken, input.tenantId, input.userId)
        val metadata = mutableMapOf<String, Any>(
            "name" to input.name,
            "mimeType" to input.mimeType
        )
        if (!input.parentFolderId.isNullOrBlank()) {
            metadata["parents"] = listOf(input.parentFolderId)
        }

        val fileBytes = Base64.getDecoder().decode(input.contentBase64)
        val multipartBody = LinkedMultiValueMap<String, Any>().apply {
            add(
                "metadata",
                jsonPart(objectMapper.writeValueAsBytes(metadata))
            )
            add(
                "media",
                binaryPart(input.name, input.mimeType, fileBytes)
            )
        }

        val responseBody = uploadClient.post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/files")
                    .queryParam("uploadType", "multipart")
                    .queryParam("fields", "id,name,mimeType,owners(emailAddress,displayName),modifiedTime,webViewLink,parents,size")
                    .queryParam("supportsAllDrives", true)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .contentType(MediaType.parseMediaType("multipart/related"))
            .body(multipartBody)
            .retrieve()
            .body(String::class.java)
            ?: "{}"

        return toDriveFile(objectMapper.readTree(responseBody))
    }

    override fun getFileMetadata(input: DriveGetFileMetadataInput): DriveFile {
        val accessToken = requireAccessToken(input.accessToken, input.tenantId, input.userId)
        val responseBody = driveClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/files/{fileId}")
                    .queryParam("fields", "id,name,mimeType,owners(emailAddress,displayName),modifiedTime,webViewLink,parents,size")
                    .queryParam("supportsAllDrives", true)
                    .build(input.fileId)
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body(String::class.java)
            ?: "{}"

        return toDriveFile(objectMapper.readTree(responseBody))
    }

    override fun startResumableUpload(input: DriveStartResumableUploadInput): DriveResumableUploadOutput {
        val accessToken = requireAccessToken(input.accessToken, input.tenantId, input.userId)
        val metadata = mutableMapOf<String, Any>("name" to input.name, "mimeType" to input.mimeType)
        if (!input.parentFolderId.isNullOrBlank()) metadata["parents"] = listOf(input.parentFolderId)
        val uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(properties.uploadBaseUrl)
            .path("/files")
            .queryParam("uploadType", "resumable")
            .queryParam("fields", "id,name,mimeType,owners(emailAddress,displayName),modifiedTime,webViewLink,parents,size")
            .queryParam("supportsAllDrives", true)
            .build().toUri()
        val request = java.net.http.HttpRequest.newBuilder(uri)
            .timeout(java.time.Duration.ofMillis(properties.readTimeoutMs))
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header("X-Upload-Content-Type", input.mimeType)
            .header("X-Upload-Content-Length", input.totalSizeBytes.toString())
            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(metadata)))
            .build()
        val response = send(request)
        require(response.statusCode() in 200..299) { "Provider rejected resumable upload initiation" }
        val uploadUri = response.headers().firstValue("Location").orElseThrow {
            IllegalStateException("Provider did not return an upload location")
        }.let(java.net.URI::create)
        val id = java.util.UUID.randomUUID().toString()
        uploadSessionStore.save(DriveUploadSession(id, uploadUri.toString(), input.totalSizeBytes))
        return uploadStatus(id)
    }

    override fun uploadChunk(input: DriveUploadChunkInput): DriveResumableUploadOutput {
        synchronized(uploadLocks.computeIfAbsent(input.uploadId) { Any() }) {
            val upload = uploadSessionStore.find(input.uploadId) ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Upload session not found")
            require(upload.file == null) { "Upload is already complete" }
            require(input.offset == upload.uploadedBytes) { "Chunk offset does not match next expected byte" }
            val chunk = Base64.getDecoder().decode(input.contentBase64)
            require(chunk.isNotEmpty()) { "Chunk cannot be empty" }
            require(chunk.size <= properties.maxChunkBytes) { "Chunk exceeds configured limit" }
            val end = input.offset + chunk.size - 1
            require(end < upload.totalSizeBytes) { "Chunk exceeds declared upload size" }
            if (end + 1 < upload.totalSizeBytes) {
                require(chunk.size % (256 * 1024) == 0) { "Non-final chunks must be a multiple of 256 KiB" }
            }
            val request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(upload.uploadUri))
                .timeout(java.time.Duration.ofMillis(properties.readTimeoutMs))
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${requireAccessToken(input.accessToken, input.tenantId, input.userId)}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header("Content-Range", "bytes ${input.offset}-$end/${upload.totalSizeBytes}")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(chunk))
                .build()
            val response = send(request)
            val updated = when (response.statusCode()) {
                200, 201 -> {
                    upload.copy(uploadedBytes = end + 1, file = toDriveFile(objectMapper.readTree(response.body())))
                }
                308 -> upload.copy(uploadedBytes = response.headers().firstValue("Range")
                    .map { it.substringAfterLast('-').toLong() + 1 }.orElse(end + 1))
                else -> throw IllegalStateException("Provider rejected upload chunk")
            }
            uploadSessionStore.save(updated)
        }
        return uploadStatus(input.uploadId)
    }

    override fun uploadStatus(uploadId: String): DriveResumableUploadOutput {
        val upload = uploadSessionStore.find(uploadId) ?: throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Upload session not found")
        return DriveResumableUploadOutput(
            uploadId = upload.id,
            uploadedBytes = upload.uploadedBytes,
            totalSizeBytes = upload.totalSizeBytes,
            complete = upload.file != null,
            file = upload.file
        )
    }

    override fun readFileText(input: DriveReadFileTextInput): DriveReadFileTextOutput {
        val accessToken = requireAccessToken(input.accessToken, input.tenantId, input.userId)
        val metadata = getFileMetadata(DriveGetFileMetadataInput(
            input.fileId, input.accessToken, input.tenantId, input.userId
        ))
        val bytes = if (metadata.mimeType.startsWith("application/vnd.google-apps.")) {
            val exportType = when (metadata.mimeType) {
                "application/vnd.google-apps.spreadsheet" -> "text/csv"
                "application/vnd.google-apps.document", "application/vnd.google-apps.presentation" -> "text/plain"
                else -> throw IllegalArgumentException("Unsupported Google Workspace file type")
            }
            driveClient.get().uri { builder ->
                builder.path("/files/{fileId}/export").queryParam("mimeType", exportType).build(input.fileId)
            }.header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve().body(ByteArray::class.java) ?: ByteArray(0)
        } else {
            driveClient.get().uri { builder ->
                builder.path("/files/{fileId}").queryParam("alt", "media").build(input.fileId)
            }.header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .header(HttpHeaders.RANGE, "bytes=0-${input.maxCharacters.toLong() * 4}")
                .retrieve().body(ByteArray::class.java) ?: ByteArray(0)
        }
        val fullText = bytes.toString(Charsets.UTF_8)
        return DriveReadFileTextOutput(
            fileId = input.fileId,
            mimeType = metadata.mimeType,
            text = fullText.take(input.maxCharacters),
            truncated = fullText.length > input.maxCharacters
        )
    }

    private fun send(request: java.net.http.HttpRequest): java.net.http.HttpResponse<ByteArray> {
        var attempt = 0
        while (true) {
            val response = try {
                resumableHttpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
                throw org.springframework.web.client.ResourceAccessException(
                    "Provider request interrupted", java.io.IOException(ex)
                )
            } catch (ex: java.io.IOException) {
                if (attempt < properties.maxRetries) {
                    attempt++
                    sleepBeforeRetry(attempt)
                    continue
                }
                throw org.springframework.web.client.ResourceAccessException("Provider request failed", ex)
            }
            if (attempt >= properties.maxRetries || (response.statusCode() != 429 && response.statusCode() !in 500..599)) {
                return response
            }
            attempt++
            sleepBeforeRetry(attempt)
        }
    }

    private fun sleepBeforeRetry(attempt: Int) {
        try {
            Thread.sleep(properties.retryBackoffMs * attempt)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw org.springframework.web.client.ResourceAccessException(
                "Provider retry interrupted", java.io.IOException(ex)
            )
        }
    }

    private fun requireAccessToken(inputAccessToken: String?, tenantId: String? = null, userId: String? = null): String {
        require((tenantId == null) == (userId == null)) { "tenantId and userId must be provided together" }
        val oauthToken = if (tenantId != null && userId != null) oauthService?.accessToken(tenantId, userId) else null
        val token = inputAccessToken?.takeIf { it.isNotBlank() } ?: oauthToken ?: properties.accessToken
        require(token.isNotBlank()) {
            "A Google Drive access token must be provided either in the request or integrations.drive.access-token"
        }
        return token
    }

    private fun buildSearchQuery(input: DriveSearchFilesInput): String {
        val clauses = mutableListOf("trashed = false")
        clauses += "name contains '${escapeQueryValue(input.query)}'"
        if (input.mimeTypes.isNotEmpty()) {
            clauses += input.mimeTypes.joinToString(" or ", prefix = "(", postfix = ")") {
                "mimeType = '${escapeQueryValue(it)}'"
            }
        }
        input.modifiedAfter?.let { clauses += "modifiedTime > '${it}'" }
        return clauses.joinToString(" and ")
    }

    private fun escapeQueryValue(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

    private fun toDriveFile(node: JsonNode): DriveFile {
        val owners = node.path("owners")
            .takeIf(JsonNode::isArray)
            ?.mapNotNull { owner ->
                owner.path("emailAddress").asText(null)
                    ?: owner.path("displayName").asText(null)
            }
            ?: emptyList()

        val modifiedTime = node.path("modifiedTime").asText(null)
            ?.let(Instant::parse)
            ?: Instant.now()

        val parents = node.path("parents")
            .takeIf(JsonNode::isArray)
            ?.map { it.asText() }
            ?: emptyList()

        return DriveFile(
            id = node.path("id").asText(""),
            name = node.path("name").asText(""),
            mimeType = node.path("mimeType").asText("application/octet-stream"),
            owners = owners,
            modifiedTime = modifiedTime,
            webViewLink = node.path("webViewLink").asText(null),
            parents = parents,
            sizeBytes = node.path("size").asText(null)?.toLongOrNull()
        )
    }

    private fun jsonPart(bytes: ByteArray): HttpEntity<ByteArrayResource> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            contentDisposition = ContentDisposition.formData().name("metadata").build()
        }
        return HttpEntity(namedResource("metadata.json", bytes), headers)
    }

    private fun binaryPart(name: String, mimeType: String, bytes: ByteArray): HttpEntity<ByteArrayResource> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType(mimeType)
            contentDisposition = ContentDisposition.formData().name("media").filename(name).build()
        }
        return HttpEntity(namedResource(name, bytes), headers)
    }

    private fun namedResource(filename: String, bytes: ByteArray): ByteArrayResource {
        return object : ByteArrayResource(bytes) {
            override fun getFilename(): String = filename
        }
    }
}
