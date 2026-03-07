package com.example.mcp.domain.drive

import com.example.mcp.domain.DriveFile
import com.example.mcp.mcp.DriveSearchFilesInput
import org.springframework.stereotype.Component
import java.time.Instant

interface GoogleDriveAdapter {
    fun searchFiles(input: DriveSearchFilesInput): Pair<List<DriveFile>, String?>
}

@Component
class StubGoogleDriveAdapter : GoogleDriveAdapter {
    override fun searchFiles(input: DriveSearchFilesInput): Pair<List<DriveFile>, String?> {
        val sample = DriveFile(
            id = "file_001",
            name = "Q4 Board Deck",
            mimeType = "application/vnd.google-apps.presentation",
            owners = listOf("finance@company.example"),
            modifiedTime = Instant.now(),
            webViewLink = "https://drive.google.com/file/d/file_001/view"
        )
        return listOf(sample) to null
    }
}
