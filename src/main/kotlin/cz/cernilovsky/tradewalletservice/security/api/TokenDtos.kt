package cz.cernilovsky.tradewalletservice.security.api

import jakarta.validation.constraints.NotBlank

data class TokenRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)
