package cz.cernilovsky.tradewalletservice.order

import com.google.common.truth.Truth.assertThat
import cz.cernilovsky.tradewalletservice.common.api.ApiError
import cz.cernilovsky.tradewalletservice.common.api.ApiErrorCode
import cz.cernilovsky.tradewalletservice.order.api.CreateOrderRequest
import cz.cernilovsky.tradewalletservice.order.api.OrderResponse
import cz.cernilovsky.tradewalletservice.order.api.UpdateOrderRequest
import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import cz.cernilovsky.tradewalletservice.support.TestAuth
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TODO(learning) Phase 2 + 5 — Optimistic locking returns HTTP 409.
 *
 * Setup:
 * - Authenticate as alice, create one PENDING order (or insert via `OrderRepository`).
 * - Read `version` from `GET /api/v1/orders/{id}`.
 *
 * Scenario A — happy path:
 * - `PATCH` with that version and a new price → 200, response `version` incremented by 1.
 *
 * Scenario B — lost update:
 * - Two threads (or two sequential PATCHes) both send the **same original version**.
 * - First PATCH 200, second PATCH **409** with code `OPTIMISTIC_LOCK`.
 * - `GET` shows only the first change and `version = original + 1`.
 *
 * Implementation notes:
 * - You need `@Version` on `OrderEntity.version` and the 409 handler in
 *   `GlobalExceptionHandler` before this test can pass.
 * - Use `saveAndFlush` in `OrderService.update` so the conflict surfaces in the request.
 */
class OrderOptimisticLockIT @Autowired constructor(
    private val jwtEncoder: JwtEncoder,
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper
) : BaseIntegrationTest() {
    @Test
    fun staleVersionOnPatchReturnsConflict() {
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")

        // 1. CREATE AN ORDER
        val orderRequest = CreateOrderRequest(
            symbol = "BTC",
            price = BigDecimal(54),
            quantity = BigDecimal(17)
        )
        val result = mockMvc.request(
            HttpMethod.POST,
            URI("/api/v1/orders")
        ) {
            configureHeaders(authorization)
            content = objectMapper.writeValueAsString(orderRequest)
        }.andReturn()
        assertEquals(HttpStatus.CREATED.value(), result.response.status)
        val orderResponse = objectMapper.readValue(result.response.contentAsString, OrderResponse::class.java)
        val originalVersion = orderResponse.version


        // 2. UPDATE THE ORDER
        val finalUpdatedExpectedPrice = BigDecimal(200)
        var updateRequest = UpdateOrderRequest(
            price = finalUpdatedExpectedPrice,
            version = originalVersion
        )
        var updateResult = mockMvc.request(
            HttpMethod.PATCH,
            URI("/api/v1/orders/${orderResponse.id}")
        ) {
            configureHeaders(authorization)
            content = objectMapper.writeValueAsString(updateRequest)
        }.andReturn()
        assertEquals(HttpStatus.OK.value(), updateResult.response.status)
        val updateResponse = objectMapper.readValue(updateResult.response.contentAsString, OrderResponse::class.java)
        val finalExpectedVersion = updateResponse.version
        assertTrue(finalExpectedVersion > originalVersion)

        // 3. UPDATE WITH SAME VERSION
        updateRequest = UpdateOrderRequest(
            price = BigDecimal(300),
            version = originalVersion
        )
        updateResult = mockMvc.request(
            HttpMethod.PATCH,
            URI("/api/v1/orders/${orderResponse.id}")
        ) {
            configureHeaders(authorization)
            content = objectMapper.writeValueAsString(updateRequest)
        }.andReturn()
        assertEquals(HttpStatus.CONFLICT.value(), updateResult.response.status)
        val apiError = objectMapper.readValue(updateResult.response.contentAsString, ApiError::class.java)
        assertEquals(409, apiError.status)
        assertEquals("OPTIMISTIC_LOCK", apiError.code)

        val getResult = mockMvc.request(
            HttpMethod.GET,
            URI("/api/v1/orders/${orderResponse.id}")
        ) {
            configureHeaders(authorization)
        }.andReturn()

        val getResponse = objectMapper.readValue(getResult.response.contentAsString, OrderResponse::class.java)
        assertThat(getResponse.price).isEqualToIgnoringScale(finalUpdatedExpectedPrice)
        assertThat(getResponse.version).isEqualTo(finalExpectedVersion)
    }
}
