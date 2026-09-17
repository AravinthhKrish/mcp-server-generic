package com.example.mcp.mcp

import com.example.mcp.auth.GoogleAuthorizationRequest
import com.example.mcp.auth.GoogleAuthorizationResponse
import com.example.mcp.auth.GoogleConnectionResponse
import com.example.mcp.auth.GoogleOAuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class GoogleOAuthController(private val service: GoogleOAuthService) {
    @PostMapping("/api/mcp/oauth/google/authorize")
    fun authorize(@Valid @RequestBody request: GoogleAuthorizationRequest): GoogleAuthorizationResponse =
        service.authorizationUrl(request)

    @GetMapping("/oauth/google/callback")
    fun callback(@RequestParam code: String, @RequestParam state: String): GoogleConnectionResponse =
        service.complete(code, state)
}
