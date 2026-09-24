package cz.cernilovsky.tradewalletservice.order

import com.google.common.truth.Truth.assertThat
import cz.cernilovsky.tradewalletservice.common.api.ApiError
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

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
    private val objectMapper: ObjectMapper,
) : BaseIntegrationTest() {
    @Test
    fun patchWithCurrentVersionUpdatesPriceAndIncrementsVersion() {
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
        val created = createOrder(authorization)
        val updatedPrice = BigDecimal(200)

        val updateResult = patchOrder(
            authorization = authorization,
            orderId = created.id,
            price = updatedPrice,
            version = created.version,
        )

        assertThat(updateResult.response.status).isEqualTo(HttpStatus.OK.value())
        val updated = objectMapper.readValue(updateResult.response.contentAsString, OrderResponse::class.java)
        assertThat(updated.price).isEqualToIgnoringScale(updatedPrice)
        assertThat(updated.version).isGreaterThan(created.version)
    }

    @Test
    fun staleVersionOnPatchReturnsConflictAndKeepsFirstChange() {
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
        val created = createOrder(authorization)
        val firstPrice = BigDecimal(200)
        val firstUpdate = patchOrder(
            authorization = authorization,
            orderId = created.id,
            price = firstPrice,
            version = created.version,
        )
        assertThat(firstUpdate.response.status).isEqualTo(HttpStatus.OK.value())
        val updated = objectMapper.readValue(firstUpdate.response.contentAsString, OrderResponse::class.java)

        val conflict = patchOrder(
            authorization = authorization,
            orderId = created.id,
            price = BigDecimal(300),
            version = created.version,
        )

        assertThat(conflict.response.status).isEqualTo(HttpStatus.CONFLICT.value())
        val apiError = objectMapper.readValue(conflict.response.contentAsString, ApiError::class.java)
        assertThat(apiError.status).isEqualTo(409)
        assertThat(apiError.code).isEqualTo("OPTIMISTIC_LOCK")

        val getResult = mockMvc.request(
            HttpMethod.GET,
            URI("/api/v1/orders/${created.id}")
        ) {
            configureHeaders(authorization)
        }.andReturn()
        assertThat(getResult.response.status).isEqualTo(HttpStatus.OK.value())
        val loaded = objectMapper.readValue(getResult.response.contentAsString, OrderResponse::class.java)
        assertThat(loaded.price).isEqualToIgnoringScale(firstPrice)
        assertThat(loaded.version).isEqualTo(updated.version)
    }

    private fun createOrder(authorization: String): OrderResponse {
        val result = mockMvc.request(
            HttpMethod.POST,
            URI("/api/v1/orders")
        ) {
            configureHeaders(authorization)
            content = objectMapper.writeValueAsString(
                CreateOrderRequest(
                    symbol = "BTC",
                    price = BigDecimal(54),
                    quantity = BigDecimal(17),
                )
            )
        }.andReturn()
        assertThat(result.response.status).isEqualTo(HttpStatus.CREATED.value())
        return objectMapper.readValue(result.response.contentAsString, OrderResponse::class.java)
    }

    private fun patchOrder(
        authorization: String,
        orderId: UUID,
        price: BigDecimal,
        version: Long,
    ): MvcResult = mockMvc.request(
        HttpMethod.PATCH,
        URI("/api/v1/orders/$orderId")
    ) {
        configureHeaders(authorization)
        content = objectMapper.writeValueAsString(UpdateOrderRequest(price = price, version = version))
    }.andReturn()
}
