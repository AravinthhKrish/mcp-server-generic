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
    fun `read file text uses bounded media request`() {
        val range = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/drive/files/text-file") { exchange ->
            if (exchange.requestURI.query.orEmpty().contains("alt=media")) {
                range.set(exchange.requestHeaders.getFirst("Range"))
                val body = "hello"
                exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.toByteArray()) }
            } else {
                respondJson(exchange, """{
                    "id":"text-file","name":"notes.txt","mimeType":"text/plain",
                    "modifiedTime":"2026-04-05T10:15:30Z","size":"5"
                }""")
            }
        }
        server.start()
        try {
            val adapter = ApiGoogleDriveAdapter(DriveProperties(
                enabled = true,
                baseUrl = "http://localhost:${server.address.port}/drive",
                accessToken = "token"
            ), ObjectMapper())
            val output = adapter.readFileText(com.example.mcp.mcp.DriveReadFileTextInput("text-file", 3))
            assertEquals("hel", output.text)
            assertTrue(output.truncated)
            assertEquals("bytes=0-12", range.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `metadata retries rate limits before succeeding`() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/drive/files/retry-file") { exchange ->
            if (calls.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(429, -1)
                exchange.close()
            } else {
                respondJson(exchange, """{
                    "id":"retry-file","name":"reel.mp4","mimeType":"video/mp4",
                    "modifiedTime":"2026-04-05T10:15:30Z","size":"5"
                }""")
            }
        }
        server.start()
        try {
            val adapter = ApiGoogleDriveAdapter(DriveProperties(
                enabled = true,
                baseUrl = "http://localhost:${server.address.port}/drive",
                uploadBaseUrl = "http://localhost:${server.address.port}/upload",
                accessToken = "token",
                maxRetries = 1,
                retryBackoffMs = 1
            ), ObjectMapper())
            assertEquals("retry-file", adapter.getFileMetadata(DriveGetFileMetadataInput("retry-file")).id)
            assertEquals(2, calls.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `resumable upload follows Google chunk protocol`() {
        val initiationHeaders = AtomicReference<com.sun.net.httpserver.Headers>()
        val ranges = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/upload/files") { exchange ->
            initiationHeaders.set(exchange.requestHeaders)
            exchange.requestBody.readAllBytes()
            exchange.responseHeaders.add("Location", "http://localhost:${server.address.port}/session/1")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.createContext("/session/1") { exchange ->
            ranges += exchange.requestHeaders.getFirst("Content-Range")
            exchange.requestBody.readAllBytes()
            if (ranges.size == 1) {
                exchange.responseHeaders.add("Range", "bytes=0-262143")
                exchange.sendResponseHeaders(308, -1)
                exchange.close()
            } else {
                respondJson(exchange, """{
                    "id":"resumable-file","name":"reel.mp4","mimeType":"video/mp4",
                    "modifiedTime":"2026-04-05T10:15:30Z","size":"262149"
                }""")
            }
        }
        server.start()
        try {
            val adapter = ApiGoogleDriveAdapter(DriveProperties(
                enabled = true,
                baseUrl = "http://localhost:${server.address.port}/drive",
                uploadBaseUrl = "http://localhost:${server.address.port}/upload",
                accessToken = "configured-token"
            ), ObjectMapper())
            val first = ByteArray(256 * 1024) { 1 }
            val second = "hello".toByteArray()
            val started = adapter.startResumableUpload(com.example.mcp.mcp.DriveStartResumableUploadInput(
                "reel.mp4", (first.size + second.size).toLong(), "video/mp4"
            ))
            assertEquals("video/mp4", initiationHeaders.get().getFirst("X-Upload-Content-Type"))
            assertEquals("262149", initiationHeaders.get().getFirst("X-Upload-Content-Length"))

            val progress = adapter.uploadChunk(com.example.mcp.mcp.DriveUploadChunkInput(
                started.uploadId, 0, java.util.Base64.getEncoder().encodeToString(first)
            ))
            assertEquals(262144, progress.uploadedBytes)
            assertTrue(!progress.complete)

            val complete = adapter.uploadChunk(com.example.mcp.mcp.DriveUploadChunkInput(
                started.uploadId, progress.uploadedBytes, java.util.Base64.getEncoder().encodeToString(second)
            ))
            assertTrue(complete.complete)
            assertEquals("resumable-file", complete.file?.id)
            assertEquals(listOf("bytes 0-262143/262149", "bytes 262144-262148/262149"), ranges)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `metadata request times out when provider does not respond`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val release = java.util.concurrent.CountDownLatch(1)
        val entered = java.util.concurrent.CountDownLatch(1)
        server.createContext("/files") { exchange ->
            entered.countDown()
            release.await(3, java.util.concurrent.TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        try {
            val adapter = ApiGoogleDriveAdapter(DriveProperties(
                enabled = true, baseUrl = "http://localhost:${server.address.port}",
                accessToken = "test", readTimeoutMs = 100
            ), ObjectMapper())
            assertThrows(org.springframework.web.client.ResourceAccessException::class.java) {
                adapter.getFileMetadata(DriveGetFileMetadataInput("file"))
            }
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

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
        val requestContentType = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/upload/files") { exchange ->
            requestContentType.set(exchange.requestHeaders.getFirst("Content-Type"))
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
            assertTrue(requestContentType.get().startsWith("multipart/related;"))
            assertTrue(requestContentType.get().contains("boundary="))
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
