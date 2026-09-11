package cz.cernilovsky.tradewalletservice.support

import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant

object TestAuth {
    fun bearerToken(jwtEncoder: JwtEncoder, username: String = "alice"): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("trade-wallet-service")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .subject(username)
            .build()
        val token = jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims),
        ).tokenValue
        return "Bearer $token"
    }
}
