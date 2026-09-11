package cz.cernilovsky.tradewalletservice.idempotency

import cz.cernilovsky.tradewalletservice.config.IdempotencyProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class IdempotencyFilter(
    private val idempotencyStore: IdempotencyStore,
    private val idempotencyProperties: IdempotencyProperties,
) : OncePerRequestFilter() {
    /**
     * TODO(learning) Phase 4 — Replay POST /api/v1/orders by X-Idempotency-Key.
     *
     * The filter is registered and currently a pass-through. Replace `doFilterInternal`.
     *
     * Algorithm:
     * 1. Read header `idempotencyProperties.headerName`. Missing → 400 (or let the controller
     *    `@RequestHeader` handle it; either is fine. Prefer filter 400 so you never hit business logic).
     * 2. Resolve `userId` from the JWT in `SecurityContextHolder` (`CurrentUser` / Jwt subject).
     *    The security filter runs before this filter if you set `@Order` correctly —
     *    `OncePerRequestFilter` beans go on the servlet chain; place this filter **after**
     *    Spring Security with `FilterRegistrationBean` + `SecurityFilterChain.addFilterAfter`
     *    **or** simply use a `HandlerInterceptor` registered in `WebMvcConfigurer` so JWT is present.
     *    Recommended: convert this to a `HandlerInterceptor` (`preHandle`/`afterCompletion`)
     *    if `SecurityContext` is empty here. If you keep a servlet filter, register it with
     *    `http.addFilterAfter(this, BearerTokenAuthenticationFilter::class.java)` in `SecurityConfig`.
     * 3. `store.get(userId, key)` → if present, write `status` + `body` + content-type and return
     *    (do not call `filterChain.doFilter`).
     * 4. `store.tryBegin` → if false, another request is in-flight: return 409 with
     *    `code=IDEMPOTENCY_IN_PROGRESS` (do not run the controller twice).
     * 5. Wrap the response (`ContentCachingResponseWrapper`), call `filterChain.doFilter`,
     *    then `copyBodyToResponse()` and `store.save` the status+body.
     *    Only cache successful 2xx (and maybe 4xx business errors). Do not cache 5xx.
     *
     * `shouldNotFilter`: only POST `/api/v1/orders`.
     *
     * Remember: Redis cache is a shortcut. Unique `(user_id, idempotency_key)` on `orders` is
     * what prevents two rows after a Redis miss.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return !(request.method.equals("POST", ignoreCase = true) && path == "/api/v1/orders")
    }
}
