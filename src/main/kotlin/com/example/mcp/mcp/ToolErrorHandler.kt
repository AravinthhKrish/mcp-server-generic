package com.example.mcp.mcp

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException

data class ToolError(val code: String, val message: String)

@RestControllerAdvice
class ToolErrorHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidInput(): ResponseEntity<ToolError> = ResponseEntity.badRequest()
        .body(ToolError("INVALID_ARGUMENT", "Invalid tool parameters"))

    @ExceptionHandler(RestClientResponseException::class)
    fun providerFailure(ex: RestClientResponseException): ResponseEntity<ToolError> {
        val status = when (ex.statusCode.value()) {
            401, 403 -> HttpStatus.FORBIDDEN
            404 -> HttpStatus.NOT_FOUND
            429 -> HttpStatus.TOO_MANY_REQUESTS
            else -> HttpStatus.BAD_GATEWAY
        }
        return ResponseEntity.status(status)
            .body(ToolError("PROVIDER_ERROR", "Provider request failed"))
    }

    @ExceptionHandler(ResourceAccessException::class)
    fun unavailable(): ResponseEntity<ToolError> = ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
        .body(ToolError("PROVIDER_UNAVAILABLE", "Provider did not respond"))
}
