package com.example.mcp.domain.gmail

import com.example.mcp.domain.MailMessage
import com.example.mcp.mcp.GmailSearchMessagesInput
import com.example.mcp.mcp.GmailGetThreadInput
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant

interface GmailAdapter {
    fun searchMessages(input: GmailSearchMessagesInput): Pair<List<MailMessage>, String?>
    fun getThread(input: GmailGetThreadInput): List<MailMessage>
}

@Component
@ConditionalOnProperty(prefix = "integrations.gmail", name = ["enabled"], havingValue = "false", matchIfMissing = true)
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

    override fun getThread(input: GmailGetThreadInput): List<MailMessage> = listOf(
        MailMessage(
            id = "msg_001", threadId = input.threadId, from = "ceo@company.example",
            to = listOf("you@company.example"), subject = "Board pack draft",
            snippet = "Please review before tomorrow", labels = listOf("INBOX"), internalDate = Instant.now()
        )
    )
}

@Component
@ConditionalOnProperty(prefix = "integrations.gmail", name = ["enabled"], havingValue = "true")
class ApiGmailAdapter(
    private val properties: GmailProperties,
    private val objectMapper: ObjectMapper
) : GmailAdapter {
    private val client = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .build()

    override fun searchMessages(input: GmailSearchMessagesInput): Pair<List<MailMessage>, String?> {
        require(properties.accessToken.isNotBlank()) {
            "integrations.gmail.access-token must be configured when integrations.gmail.enabled=true"
        }

        val listBody = client.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/users/{userId}/messages")
                    .queryParam("q", input.query)
                    .queryParam("maxResults", input.maxResults)
                    .queryParamIfPresent("pageToken", java.util.Optional.ofNullable(input.pageToken))
                    .build(properties.userId)
            }
            .header("Authorization", "Bearer ${properties.accessToken}")
            .retrieve()
            .body(String::class.java)
            ?: "{}"

        val listNode = objectMapper.readTree(listBody)
        val nextPageToken = listNode.path("nextPageToken").asText(null)

        val messages = listNode.path("messages")
            .takeIf(JsonNode::isArray)
            ?.mapNotNull { messageRef -> messageRef.path("id").asText(null) }
            ?.map { messageId -> getMessage(messageId) }
            ?: emptyList()

        return messages to nextPageToken
    }

    override fun getThread(input: GmailGetThreadInput): List<MailMessage> {
        require(properties.accessToken.isNotBlank()) {
            "integrations.gmail.access-token must be configured when integrations.gmail.enabled=true"
        }
        val body = client.get().uri { builder ->
            builder.path("/users/{userId}/threads/{threadId}")
                .queryParam("format", "metadata")
                .queryParam("metadataHeaders", "From")
                .queryParam("metadataHeaders", "To")
                .queryParam("metadataHeaders", "Subject")
                .build(properties.userId, input.threadId)
        }.header("Authorization", "Bearer ${properties.accessToken}")
            .retrieve().body(String::class.java) ?: "{}"
        return objectMapper.readTree(body).path("messages")
            .takeIf(JsonNode::isArray)?.map(::toMessage) ?: emptyList()
    }

    private fun getMessage(messageId: String): MailMessage {
        val messageBody = client.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/users/{userId}/messages/{messageId}")
                    .queryParam("format", "metadata")
                    .queryParam("metadataHeaders", "From")
                    .queryParam("metadataHeaders", "To")
                    .queryParam("metadataHeaders", "Subject")
                    .build(properties.userId, messageId)
            }
            .header("Authorization", "Bearer ${properties.accessToken}")
            .retrieve()
            .body(String::class.java)
            ?: "{}"

        return toMessage(objectMapper.readTree(messageBody), messageId)
    }

    private fun toMessage(node: JsonNode, fallbackId: String = ""): MailMessage {
        val payload = node.path("payload")
        val headers = payload.path("headers")

        val internalDate = node.path("internalDate").asText("0").toLongOrNull() ?: 0L

        return MailMessage(
            id = node.path("id").asText(fallbackId),
            threadId = node.path("threadId").asText(""),
            from = headerValue(headers, "From") ?: "",
            to = headerValue(headers, "To")?.split(',')?.map(String::trim)?.filter(String::isNotBlank) ?: emptyList(),
            subject = headerValue(headers, "Subject") ?: "",
            snippet = node.path("snippet").asText(""),
            labels = node.path("labelIds").takeIf(JsonNode::isArray)?.map { it.asText() } ?: emptyList(),
            internalDate = Instant.ofEpochMilli(internalDate)
        )
    }

    private fun headerValue(headers: JsonNode, name: String): String? {
        if (!headers.isArray) return null
        return headers.firstOrNull { header ->
            header.path("name").asText("").equals(name, ignoreCase = true)
        }?.path("value")?.asText(null)
    }
}
