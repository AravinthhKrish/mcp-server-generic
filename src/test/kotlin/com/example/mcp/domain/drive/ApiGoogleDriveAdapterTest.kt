package com.example.mcp.domain.drive

import com.example.mcp.mcp.DriveCreateFolderInput
import com.example.mcp.mcp.DriveGetFileMetadataInput
import com.example.mcp.mcp.DriveSearchFilesInput
import com.example.mcp.mcp.DriveUploadFileInput
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class ApiGoogleDriveAdapterTest {
    @Test
    fun `create folder prefers request token over configured token`() {
        val lastAuthHeader = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/drive/files") { exchange ->
            lastAuthHeader.set(exchange.requestHeaders.getFirst("Authorization"))
            respondJson(
                exchange,
                """
                {
                  "id":"folder_live_001",
                  "name":"Reels",
                  "mimeType":"application/vnd.google-apps.folder",
                  "modifiedTime":"2026-04-05T10:15:30Z",
                  "webViewLink":"https://drive.google.com/drive/folders/folder_live_001",
                  "parents":["root"]
                }
                """.trimIndent()
            )
        }
        server.start()

        try {
            val baseUrl = "http://localhost:${server.address.port}/drive"
            val adapter = ApiGoogleDriveAdapter(
                DriveProperties(
                    enabled = true,
                    baseUrl = baseUrl,
                    uploadBaseUrl = baseUrl,
                    accessToken = "configured-token"
                ),
                ObjectMapper()
            )

            val folder = adapter.createFolder(
                DriveCreateFolderInput(
                    name = "Reels",
                    parentFolderId = "root",
                    accessToken = "request-token"
                )
            )

            assertEquals("request-token", lastAuthHeader.get()?.removePrefix("Bearer "))
            assertEquals("folder_live_001", folder.id)
            assertEquals(listOf("root"), folder.parents)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `upload file sends multipart body and returns normalized file`() {
        val requestBody = AtomicReference<String>()
        val requestAuth = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/upload/files") { exchange ->
            requestAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            requestBody.set(exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8))
            respondJson(
                exchange,
                """
                {
                  "id":"file_live_001",
                  "name":"reel.mp4",
                  "mimeType":"video/mp4",
                  "modifiedTime":"2026-04-05T10:15:30Z",
                  "webViewLink":"https://drive.google.com/file/d/file_live_001/view",
                  "parents":["folder_123"],
                  "size":"5"
                }
                """.trimIndent()
            )
        }
        server.start()

        try {
            val adapter = ApiGoogleDriveAdapter(
                DriveProperties(
                    enabled = true,
                    baseUrl = "http://localhost:${server.address.port}/drive",
                    uploadBaseUrl = "http://localhost:${server.address.port}/upload",
                    accessToken = "configured-token"
                ),
                ObjectMapper()
            )

            val file = adapter.uploadFile(
                DriveUploadFileInput(
                    name = "reel.mp4",
                    contentBase64 = "aGVsbG8=",
                    mimeType = "video/mp4",
                    parentFolderId = "folder_123"
                )
            )

            assertEquals("configured-token", requestAuth.get()?.removePrefix("Bearer "))
            assertTrue(requestBody.get().contains("name=\"metadata\""))
            assertTrue(requestBody.get().contains("name=\"media\""))
            assertTrue(requestBody.get().contains("\"name\":\"reel.mp4\""))
            assertEquals("file_live_001", file.id)
            assertEquals(5L, file.sizeBytes)
            assertEquals(listOf("folder_123"), file.parents)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `file metadata falls back to configured token`() {
        val lastAuthHeader = AtomicReference<String>()
        val lastPath = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/drive/files/file_live_001") { exchange ->
            lastAuthHeader.set(exchange.requestHeaders.getFirst("Authorization"))
            lastPath.set(exchange.requestURI.toString())
            respondJson(
                exchange,
                """
                {
                  "id":"file_live_001",
                  "name":"uploaded-reel.mp4",
                  "mimeType":"video/mp4",
                  "modifiedTime":"2026-04-05T10:15:30Z",
                  "webViewLink":"https://drive.google.com/file/d/file_live_001/view",
                  "parents":["folder_123"],
                  "size":"42"
                }
                """.trimIndent()
            )
        }
        server.start()

        try {
            val baseUrl = "http://localhost:${server.address.port}/drive"
            val adapter = ApiGoogleDriveAdapter(
                DriveProperties(
                    enabled = true,
                    baseUrl = baseUrl,
                    uploadBaseUrl = baseUrl,
                    accessToken = "configured-token"
                ),
                ObjectMapper()
            )

            val file = adapter.getFileMetadata(DriveGetFileMetadataInput(fileId = "file_live_001"))

            assertEquals("configured-token", lastAuthHeader.get()?.removePrefix("Bearer "))
            assertTrue(lastPath.get().contains("fields="))
            assertEquals("uploaded-reel.mp4", file.name)
            assertEquals(42L, file.sizeBytes)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `search files builds drive query with mime type and timestamp filters`() {
        val lastQuery = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/drive/files") { exchange ->
            lastQuery.set(exchange.requestURI.query.orEmpty())
            respondJson(
                exchange,
                """
                {
                  "files":[
                    {
                      "id":"file_001",
                      "name":"reel-final.mp4",
                      "mimeType":"video/mp4",
                      "modifiedTime":"2026-04-05T10:15:30Z",
                      "parents":["folder_123"],
                      "size":"100"
                    }
                  ],
                  "nextPageToken":"next-token"
                }
                """.trimIndent()
            )
        }
        server.start()

        try {
            val baseUrl = "http://localhost:${server.address.port}/drive"
            val adapter = ApiGoogleDriveAdapter(
                DriveProperties(
                    enabled = true,
                    baseUrl = baseUrl,
                    uploadBaseUrl = baseUrl,
                    accessToken = "configured-token"
                ),
                ObjectMapper()
            )

            val (files, nextPageToken) = adapter.searchFiles(
                DriveSearchFilesInput(
                    query = "reel's",
                    mimeTypes = listOf("video/mp4"),
                    modifiedAfter = Instant.parse("2026-04-01T00:00:00Z"),
                    pageSize = 10,
                    pageToken = "page-2"
                )
            )

            val decodedQuery = URLDecoder.decode(lastQuery.get(), StandardCharsets.UTF_8)
            assertTrue(decodedQuery.contains("pageToken=page-2"))
            assertTrue(decodedQuery.contains("name contains 'reel"))
            assertTrue(decodedQuery.contains("mimeType = 'video/mp4'"))
            assertTrue(decodedQuery.contains("modifiedTime > '2026-04-01T00:00:00Z'"))
            assertEquals(1, files.size)
            assertEquals("next-token", nextPageToken)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `upload file rejects invalid base64 content before making request`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.start()

        try {
            val baseUrl = "http://localhost:${server.address.port}/drive"
            val adapter = ApiGoogleDriveAdapter(
                DriveProperties(
                    enabled = true,
                    baseUrl = baseUrl,
                    uploadBaseUrl = baseUrl,
                    accessToken = "configured-token"
                ),
                ObjectMapper()
            )

            assertThrows(IllegalArgumentException::class.java) {
                adapter.uploadFile(
                    DriveUploadFileInput(
                        name = "broken.mp4",
                        contentBase64 = "%%%not-base64%%%",
                        mimeType = "video/mp4"
                    )
                )
            }
        } finally {
            server.stop(0)
        }
    }

    private fun respondJson(exchange: HttpExchange, body: String) {
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(body.toByteArray()) }
    }
}
