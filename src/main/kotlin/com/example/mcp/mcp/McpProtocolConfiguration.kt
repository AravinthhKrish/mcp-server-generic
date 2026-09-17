package com.example.mcp.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import java.time.Duration

@Configuration
class McpProtocolConfiguration {
    @Bean
    fun mcpTransport(objectMapper: ObjectMapper): WebFluxStreamableServerTransportProvider =
        WebFluxStreamableServerTransportProvider.builder()
            .jsonMapper(JacksonMcpJsonMapper(objectMapper))
            .messageEndpoint("/mcp")
            .keepAliveInterval(Duration.ofSeconds(30))
            .build()

    @Bean
    fun mcpRouter(transport: WebFluxStreamableServerTransportProvider): RouterFunction<*> =
        transport.routerFunction

    @Bean(destroyMethod = "closeGracefully")
    fun mcpServer(
        transport: WebFluxStreamableServerTransportProvider,
        api: ApiMcpController,
        objectMapper: ObjectMapper
    ): McpSyncServer {
        val tools = api.tools().map { spec ->
            McpServerFeatures.SyncToolSpecification.builder()
                .tool(toMcpTool(spec))
                .callHandler { _, request ->
                    try {
                        val response = runBlocking {
                            api.execute(ExecuteToolRequest(toolId = spec.id, params = request.arguments()))
                        }
                        val structured = mapOf(
                            "success" to response.success,
                            "toolId" to response.toolId,
                            "toolName" to response.toolName,
                            "result" to response.result,
                            "simulated" to response.simulated
                        )
                        McpSchema.CallToolResult.builder()
                            .addTextContent(objectMapper.writeValueAsString(structured))
                            .structuredContent(structured)
                            .isError(false)
                            .build()
                    } catch (_: Exception) {
                        McpSchema.CallToolResult.builder()
                            .addTextContent("Tool execution failed")
                            .isError(true)
                            .build()
                    }
                }
                .build()
        }

        return McpServer.sync(transport)
            .serverInfo("mcp-server-generic", "0.1.0")
            .instructions("Data access tools for Drive, Gmail, news, web search and market data.")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
            .strictToolNameValidation(false)
            .jsonSchemaValidator(DefaultJsonSchemaValidator(objectMapper))
            .requestTimeout(Duration.ofSeconds(90))
            .tools(tools)
            .build()
    }

    private fun toMcpTool(spec: ToolSpec): McpSchema.Tool {
        val properties = spec.params.associate { parameter ->
            parameter.name to when (parameter.type) {
                "array" -> mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to parameter.description)
                "number" -> mapOf("type" to "number", "description" to parameter.description)
                "boolean" -> mapOf("type" to "boolean", "description" to parameter.description)
                else -> mapOf("type" to "string", "description" to parameter.description)
            }
        }
        val schema = McpSchema.JsonSchema(
            "object",
            properties,
            spec.params.filter { it.required }.map { it.name },
            false,
            emptyMap(),
            emptyMap()
        )
        return McpSchema.Tool.builder()
            .name(spec.id)
            .title(spec.name)
            .description(spec.description)
            .inputSchema(schema)
            .build()
    }
}
