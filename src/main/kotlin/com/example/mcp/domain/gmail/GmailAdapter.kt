package com.example.mcp.domain.gmail

import com.example.mcp.domain.MailMessage
import com.example.mcp.mcp.GmailSearchMessagesInput
import org.springframework.stereotype.Component
import java.time.Instant

interface GmailAdapter {
    fun searchMessages(input: GmailSearchMessagesInput): Pair<List<MailMessage>, String?>
}

@Component
class StubGmailAdapter : GmailAdapter {
    override fun searchMessages(input: GmailSearchMessagesInput): Pair<List<MailMessage>, String?> {
        val sample = MailMessage(
            id = "msg_001",
            threadId = "thr_001",
            from = "ceo@company.example",
            to = listOf("you@company.example"),
            subject = "Board pack draft",
            snippet = "Please review before tomorrow",
            labels = listOf("INBOX", "IMPORTANT"),
            internalDate = Instant.now()
        )
        return listOf(sample) to null
    }
}
