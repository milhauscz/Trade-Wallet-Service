package cz.cernilovsky.tradewalletservice.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class CurrentUser {
    fun userId(): String {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: error("No authentication in security context")
        return when (val principal = authentication.principal) {
            is Jwt -> principal.subject ?: error("JWT has no subject")
            is UserDetails -> principal.username
            is String -> principal
            else -> authentication.name
        }
    }
}
