package cz.cernilovsky.tradewalletservice.idempotency

import com.google.common.truth.Truth.assertThat
import cz.cernilovsky.tradewalletservice.order.api.CreateOrderRequest
import cz.cernilovsky.tradewalletservice.order.api.OrderResponse
import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import cz.cernilovsky.tradewalletservice.support.TestAuth
import cz.cernilovsky.tradewalletservice.wallet.persistence.WalletRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.test.assertEquals

/**
 * TODO(learning) Phase 4 + 5 — Idempotency key replays the original response.
 *
 * Setup:
 * - Authenticate as alice. Pick one UUID as `X-Idempotency-Key`.
 *
 * Scenario A — retry after success:
 * - `POST /api/v1/orders` twice with the **same** key and the **same** body.
 * - First: 201, one row in `orders`, wallet reserved once.
 * - Second: 201 (or 200 if you choose replay-as-200 — pick one and stick to it),
 *   **same order id**, `orders` count still 1, `reservedAmount` unchanged.
 *
 * Scenario B — uniqueness without Redis:
 * - Flush Redis (`redisTemplate.connectionFactory.connection.serverCommands().flushAll()`)
 *   after the first POST, then retry with the same key.
 * - Still a single order row (DB unique / `findByUserIdAndIdempotencyKey`).
 *
 * Scenario C — concurrent duplicates:
 * - Two parallel POSTs with the same key: exactly one 201 from business logic,
 *   the other is either a cached replay or 409 in-progress then retry → still one row.
 */
class IdempotencyIT @Autowired constructor(
    private val jwtEncoder: JwtEncoder,
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val redis: StringRedisTemplate,
    private val walletRepository: WalletRepository
) : BaseIntegrationTest() {
    @Test
    fun duplicateIdempotencyKeyDoesNotCreateSecondOrder() {
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
        var idempotencyKey = UUID.randomUUID().toString()

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
            configureHeaders(authorization, idempotencyKey = idempotencyKey)
            content = objectMapper.writeValueAsString(orderRequest)
        }.andReturn()
        assertEquals(HttpStatus.CREATED.value(), result.response.status)
        val orderResponse = objectMapper.readValue(result.response.contentAsString, OrderResponse::class.java)
        val reservedAmountAfterInitialOrder = walletRepository.findByUserId("alice")!!.reservedAmount

        // 2. SEND A DUPLICATE REQUEST
        var duplicateResult = mockMvc.request(
            HttpMethod.POST,
            URI("/api/v1/orders")
        ) {
            configureHeaders(authorization, idempotencyKey = idempotencyKey)
            content = objectMapper.writeValueAsString(orderRequest)
        }.andReturn()
        assertEquals(HttpStatus.CREATED.value(), duplicateResult.response.status)
        var duplicateOrderResponse = objectMapper.readValue(duplicateResult.response.contentAsString, OrderResponse::class.java)
        assertThat(duplicateOrderResponse.id).isEqualTo(orderResponse.id)
        assertThat(duplicateOrderResponse.price).isEquivalentAccordingToCompareTo(orderResponse.price)
        assertThat(duplicateOrderResponse.quantity).isEquivalentAccordingToCompareTo(orderResponse.quantity)
        assertThat(walletRepository.findByUserId("alice")!!.reservedAmount).isEqualTo(reservedAmountAfterInitialOrder)

        // 3. FLUSH REDIS AND MAKE A DUPLICATE AGAIN
        redis.execute { connection ->
            connection.serverCommands().flushAll()
        }
        duplicateResult = mockMvc.request(
            HttpMethod.POST,
            URI("/api/v1/orders")
        ) {
            configureHeaders(authorization, idempotencyKey = idempotencyKey)
            content = objectMapper.writeValueAsString(orderRequest)
        }.andReturn()
        assertEquals(HttpStatus.CREATED.value(), duplicateResult.response.status)
        duplicateOrderResponse = objectMapper.readValue(duplicateResult.response.contentAsString, OrderResponse::class.java)
        assertThat(duplicateOrderResponse.id).isEqualTo(orderResponse.id)
        assertThat(duplicateOrderResponse.price).isEquivalentAccordingToCompareTo(orderResponse.price)
        assertThat(duplicateOrderResponse.quantity).isEquivalentAccordingToCompareTo(orderResponse.quantity)
        assertThat(walletRepository.findByUserId("alice")!!.reservedAmount).isEqualTo(reservedAmountAfterInitialOrder)

        // 4. TWO PARALLEL DUPLICATE POSTS
        val createdCount = AtomicInteger(0)
        val inProgressCount = AtomicInteger(0)
        val ids = Collections.synchronizedList(mutableListOf<UUID>())

        idempotencyKey = UUID.randomUUID().toString()
        IntStream.range(0, 2).parallel().forEach {
            val parallelResult = mockMvc.request(
                HttpMethod.POST,
                URI("/api/v1/orders")
            ) {
                configureHeaders(authorization, idempotencyKey = idempotencyKey)
                content = objectMapper.writeValueAsString(orderRequest)
            }.andReturn()
            when (parallelResult.response.status) {
                201 -> {
                    createdCount.incrementAndGet()
                    val parallelOrderResponse = objectMapper.readValue(parallelResult.response.contentAsString, OrderResponse::class.java)
                    ids.add(parallelOrderResponse.id)
                }
                409 -> inProgressCount.incrementAndGet()
            }
        }
        if (createdCount.get() == 2) {
            assertThat(ids).hasSize(2)
            assertThat(inProgressCount.get()).isEqualTo(0)
            assertThat(ids[0]).isEqualTo(ids[1])
        } else if (createdCount.get() == 1) {
            assertThat(inProgressCount.get()).isEqualTo(1)
            assertThat(ids).hasSize(1)
        } else {
            throw AssertionError("Unexpected created count: ${createdCount.get()}")
        }
        assertThat(walletRepository.findByUserId("alice")!!.reservedAmount).isEqualToIgnoringScale(reservedAmountAfterInitialOrder + orderRequest.price * orderRequest.quantity)
    }
}
