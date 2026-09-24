package cz.cernilovsky.tradewalletservice.idempotency

import cz.cernilovsky.tradewalletservice.common.api.ApiErrorCode
import cz.cernilovsky.tradewalletservice.common.security.CurrentUser
import cz.cernilovsky.tradewalletservice.config.IdempotencyProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import tools.jackson.databind.ObjectMapper

@Component
class IdempotencyFilter(
    private val idempotencyStore: IdempotencyStore,
    private val idempotencyProperties: IdempotencyProperties,
    private val currentUser: CurrentUser,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    // Replays POST /api/v1/orders when X-Idempotency-Key was already completed.
    // A missing header is 400. A key still in progress is 409.
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val idempotencyHeader = request.getHeader(idempotencyProperties.headerName)
        if (idempotencyHeader == null) {
            response.sendErrorResponse(ApiErrorCode.MISSING_HEADER)
            return
        }

        val cachedResponse = idempotencyStore.get(currentUser.userId(), idempotencyHeader)
        if (cachedResponse != null) {
            response.status = cachedResponse.status
            response.contentType = cachedResponse.contentType
            response.outputStream.write(cachedResponse.body.toByteArray())
            return
        }

        if (!idempotencyStore.tryBegin(currentUser.userId(), idempotencyHeader)) {
            response.sendErrorResponse(ApiErrorCode.IDEMPOTENCY_IN_PROGRESS)
            return
        }

        val wrappedResponse = ContentCachingResponseWrapper(response)
        filterChain.doFilter(request, wrappedResponse)
        wrappedResponse.copyBodyToResponse()
        idempotencyStore.processResponse(currentUser.userId(), idempotencyHeader, CachedHttpResponse(wrappedResponse.status, String(wrappedResponse.contentAsByteArray), wrappedResponse.contentType ?: "application/json"))
    }

    fun HttpServletResponse.sendErrorResponse(code: ApiErrorCode) {
        status = code.status
        contentType = "application/json"
        writer.write(
            objectMapper.writeValueAsString(
                code.createApiError(code.message)
            )
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return !(request.method.equals("POST", ignoreCase = true) && path == "/api/v1/orders")
    }
}
