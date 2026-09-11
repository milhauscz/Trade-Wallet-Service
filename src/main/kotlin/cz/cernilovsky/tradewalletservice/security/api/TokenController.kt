package cz.cernilovsky.tradewalletservice.security.api

import cz.cernilovsky.tradewalletservice.security.TokenService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class TokenController(
    private val tokenService: TokenService,
) {
    @PostMapping("/token")
    fun token(@Valid @RequestBody request: TokenRequest): TokenResponse = tokenService.issueToken(request)
}
