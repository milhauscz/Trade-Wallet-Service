package cz.cernilovsky.tradewalletservice.idempotency

import com.google.common.truth.Truth.assertThat
import cz.cernilovsky.tradewalletservice.order.api.CreateOrderRequest
import cz.cernilovsky.tradewalletservice.order.api.OrderResponse
import cz.cernilovsky.tradewalletservice.support.BaseIntegrationTest
import cz.cernilovsky.tradewalletservice.support.TestAuth
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

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
) : BaseIntegrationTest() {
    @Test
    fun retryWithSameKeyReplaysTheOriginalOrder() {
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
        val idempotencyKey = UUID.randomUUID().toString()
        val orderRequest = sampleOrder()

        val created = postOrder(authorization, idempotencyKey, orderRequest)
        assertThat(created.response.status).isEqualTo(HttpStatus.CREATED.value())
        val orderResponse = readOrder(created)
        val reservedAfterCreate = aliceReservedAmount()

        val replay = postOrder(authorization, idempotencyKey, orderRequest)
        assertThat(replay.response.status).isEqualTo(HttpStatus.CREATED.value())
        assertSameOrder(readOrder(replay), orderResponse)
        assertThat(aliceReservedAmount()).isEqualTo(reservedAfterCreate)
    }

    @Test
    fun retryAfterRedisFlushStillReturnsTheSameOrder() {
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
        val idempotencyKey = UUID.randomUUID().toString()
        val orderRequest = sampleOrder()

        val created = postOrder(authorization, idempotencyKey, orderRequest)
        assertThat(created.response.status).isEqualTo(HttpStatus.CREATED.value())
        val orderResponse = readOrder(created)
        val reservedAfterCreate = aliceReservedAmount()

        redis.execute { connection -> connection.serverCommands().flushAll() }

        val replay = postOrder(authorization, idempotencyKey, orderRequest)
        assertThat(replay.response.status).isEqualTo(HttpStatus.CREATED.value())
        assertSameOrder(readOrder(replay), orderResponse)
        assertThat(aliceReservedAmount()).isEqualTo(reservedAfterCreate)
    }

    @Test
    fun parallelPostsWithSameKeyReserveFundsOnce() {
        val authorization = TestAuth.bearerToken(jwtEncoder, "alice")
        val idempotencyKey = UUID.randomUUID().toString()
        val orderRequest = sampleOrder()
        val reservedBefore = aliceReservedAmount()
        val createdCount = AtomicInteger(0)
        val inProgressCount = AtomicInteger(0)
        val ids = Collections.synchronizedList(mutableListOf<UUID>())

        IntStream.range(0, 2).parallel().forEach {
            val parallelResult = postOrder(authorization, idempotencyKey, orderRequest)
            when (parallelResult.response.status) {
                HttpStatus.CREATED.value() -> {
                    createdCount.incrementAndGet()
                    ids.add(readOrder(parallelResult).id)
                }
                HttpStatus.CONFLICT.value() -> inProgressCount.incrementAndGet()
            }
        }

        if (createdCount.get() == 2) {
            assertThat(inProgressCount.get()).isEqualTo(0)
            assertThat(ids).containsExactly(ids.first(), ids.first())
        } else if (createdCount.get() == 1) {
            assertThat(inProgressCount.get()).isEqualTo(1)
            assertThat(ids).hasSize(1)
        } else {
            throw AssertionError("Unexpected created count: ${createdCount.get()}")
        }
        assertThat(aliceReservedAmount()).isEqualToIgnoringScale(
            reservedBefore + orderRequest.price * orderRequest.quantity
        )
    }

    private fun sampleOrder() = CreateOrderRequest(
        symbol = "BTC",
        price = BigDecimal(54),
        quantity = BigDecimal(17),
    )

    private fun postOrder(
        authorization: String,
        idempotencyKey: String,
        request: CreateOrderRequest,
    ): MvcResult = mockMvc.request(
        HttpMethod.POST,
        URI("/api/v1/orders")
    ) {
        configureHeaders(authorization, idempotencyKey = idempotencyKey)
        content = objectMapper.writeValueAsString(request)
    }.andReturn()

    private fun readOrder(result: MvcResult): OrderResponse =
        objectMapper.readValue(result.response.contentAsString, OrderResponse::class.java)

    private fun assertSameOrder(actual: OrderResponse, expected: OrderResponse) {
        assertThat(actual.id).isEqualTo(expected.id)
        assertThat(actual.price).isEquivalentAccordingToCompareTo(expected.price)
        assertThat(actual.quantity).isEquivalentAccordingToCompareTo(expected.quantity)
    }

    private fun aliceReservedAmount(): BigDecimal =
        walletRepository.findByUserId("alice")!!.reservedAmount
}
