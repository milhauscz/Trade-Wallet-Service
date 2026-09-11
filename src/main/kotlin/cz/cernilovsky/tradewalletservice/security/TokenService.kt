package cz.cernilovsky.tradewalletservice.security

import cz.cernilovsky.tradewalletservice.config.JwtProperties
import cz.cernilovsky.tradewalletservice.security.api.TokenRequest
import cz.cernilovsky.tradewalletservice.security.api.TokenResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TokenService(
    private val authenticationManager: AuthenticationManager,
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
) {
    fun issueToken(request: TokenRequest): TokenResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username, request.password),
        )
        val now = Instant.now()
        val expiresAt = now.plus(jwtProperties.ttl)
        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(request.username)
            .build()
        val token = jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims),
        ).tokenValue
        return TokenResponse(
            accessToken = token,
            expiresIn = jwtProperties.ttl.toSeconds(),
        )
    }
}
