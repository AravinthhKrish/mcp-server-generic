package com.example.mcp.domain.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.format.DateTimeFormatter


fun Instant.toDateString(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return formatter.format(this)
}


fun ObjectMapper.parseNodes(data: String): JsonNode {
    val root = unwrapTextNode(this.readTree(data), this)
    return when {
        root.has("result") && root.path("result").has("content") -> {
            val content = root.path("result").path("content")
            val textNode = content
                .firstOrNull { it.path("type").asText() == "text" }
                ?.path("text")

            when {
                textNode == null || textNode.isMissingNode || textNode.isNull -> this.createArrayNode()
                textNode.isTextual -> {
                    val text = textNode.asText().trim()
                    if (text.isBlank()) this.createArrayNode() else this.readTree(text)
                }

                textNode.isArray -> textNode
                else -> this.createArrayNode()
            }
        }

        root.has("content") -> {
            val content = root.path("content")
            val textNode = content
                .firstOrNull { it.path("type").asText() == "text" }
                ?.path("text")

            when {
                textNode == null || textNode.isMissingNode || textNode.isNull -> this.createArrayNode()
                textNode.isTextual -> {
                    val text = textNode.asText().trim()
                    if (text.isBlank()) this.createArrayNode() else this.readTree(text)
                }

                textNode.isArray -> textNode
                else -> this.createArrayNode()
            }
        }

        root.isArray -> root

        else -> this.createArrayNode()
    }
}

private fun unwrapTextNode(node: JsonNode, objectMapper: ObjectMapper): JsonNode {
    var current = node
    var depth = 0
    while (current.isTextual && depth < 3) {
        val parsed =objectMapper.readTree(current.asText())
        current = parsed
        depth++
    }
    return current
}