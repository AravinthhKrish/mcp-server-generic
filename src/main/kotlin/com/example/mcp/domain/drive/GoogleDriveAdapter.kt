package com.example.mcp.domain.drive

import com.example.mcp.domain.DriveFile
import com.example.mcp.mcp.DriveCreateFolderInput
import com.example.mcp.mcp.DriveGetFileMetadataInput
import com.example.mcp.mcp.DriveSearchFilesInput
import com.example.mcp.mcp.DriveUploadFileInput
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
}

@Component
@ConditionalOnProperty(prefix = "integrations.drive", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class StubGoogleDriveAdapter : GoogleDriveAdapter {
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
        return DriveFile(
            id = "folder_001",
            name = input.name,
            mimeType = "application/vnd.google-apps.folder",
            owners = listOf("creator@company.example"),
            modifiedTime = Instant.now(),
            webViewLink = "https://drive.google.com/drive/folders/folder_001",
            parents = listOfNotNull(input.parentFolderId)
        )
    }

    override fun uploadFile(input: DriveUploadFileInput): DriveFile {
        return DriveFile(
            id = "file_upload_001",
            name = input.name,
            mimeType = input.mimeType,
            owners = listOf("uploader@company.example"),
            modifiedTime = Instant.now(),
            webViewLink = "https://drive.google.com/file/d/file_upload_001/view",
            parents = listOfNotNull(input.parentFolderId),
            sizeBytes = decodedSize(input.contentBase64)
        )
    }

    override fun getFileMetadata(input: DriveGetFileMetadataInput): DriveFile {
        return DriveFile(
            id = input.fileId,
            name = "uploaded-reel.mp4",
            mimeType = "video/mp4",
            owners = listOf("uploader@company.example"),
            modifiedTime = Instant.now(),
            webViewLink = "https://drive.google.com/file/d/${input.fileId}/view",
            parents = listOf("folder_001"),
            sizeBytes = 1024L
        )
    }

    private fun decodedSize(contentBase64: String): Long {
        return runCatching { Base64.getDecoder().decode(contentBase64).size.toLong() }
            .getOrDefault(contentBase64.length.toLong())
    }
}

@Component
@ConditionalOnProperty(prefix = "integrations.drive", name = ["enabled"], havingValue = "true")
class ApiGoogleDriveAdapter(
    private val properties: DriveProperties,
    private val objectMapper: ObjectMapper
) : GoogleDriveAdapter {
    private val driveClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .build()

    private val uploadClient = RestClient.builder()
        .baseUrl(properties.uploadBaseUrl)
        .build()

    override fun searchFiles(input: DriveSearchFilesInput): Pair<List<DriveFile>, String?> {
        val accessToken = requireAccessToken(inputAccessToken = null)
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
        val accessToken = requireAccessToken(input.accessToken)
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
        val accessToken = requireAccessToken(input.accessToken)
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
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(multipartBody)
            .retrieve()
            .body(String::class.java)
            ?: "{}"

        return toDriveFile(objectMapper.readTree(responseBody))
    }

    override fun getFileMetadata(input: DriveGetFileMetadataInput): DriveFile {
        val accessToken = requireAccessToken(input.accessToken)
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

    private fun requireAccessToken(inputAccessToken: String?): String {
        val token = inputAccessToken?.takeIf { it.isNotBlank() } ?: properties.accessToken
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

    private fun escapeQueryValue(value: String): String = value.replace("'", "\\'")

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
